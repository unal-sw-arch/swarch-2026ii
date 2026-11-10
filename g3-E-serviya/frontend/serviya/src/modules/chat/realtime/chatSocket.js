/**
 * Cliente WebSocket del chat (protocolo en backend/chat-service/docs/asyncapi.yaml).
 *
 * Sin React ni DOM: recibe sus dependencias, así se puede probar con Node contra el servicio real.
 *  - Autenticación por ticket de un solo uso (POST /api/chat/v1/ws-tickets); nunca pone el JWT en la URL.
 *  - Reconexión automática con backoff exponencial + jitter. Al reconectar pide `sync` de lo que se perdió.
 *  - Latido propio (ping → pong) para detectar conexiones muertas que el navegador aún cree abiertas.
 *  - `request()` correlaciona la respuesta por `id` de frame (message.ack, sync.result, pong o error).
 *
 * Estados: idle → connecting → open → (reconnecting → open)* ; `suspended` (4403) y `stopped` son finales.
 */

export const SOCKET_STATES = Object.freeze({
    IDLE: 'idle',
    CONNECTING: 'connecting',
    OPEN: 'open',
    RECONNECTING: 'reconnecting',
    SUSPENDED: 'suspended',
    UNAVAILABLE: 'unavailable',
    STOPPED: 'stopped',
});

const CLOSE_INVALID_TICKET = 4401;
const CLOSE_SUSPENDED = 4403;
const REPLY_TYPES = new Set(['message.ack', 'sync.result', 'pong']);

export class ChatError extends Error {
    constructor(code, message) {
        super(message || code);
        this.code = code;
    }
}

export class ChatSocket {
    /**
     * @param {object} deps
     * @param {() => Promise<{ticket: string}>} deps.issueTicket pide un ticket (REST, con el JWT)
     * @param {(ticket: string) => string} deps.wsUrl URL del WebSocket para ese ticket
     * @param {typeof WebSocket} [deps.WebSocketImpl]
     * @param {object} [deps.options] tiempos (ms), configurables para pruebas
     */
    constructor({ issueTicket, wsUrl, WebSocketImpl = globalThis.WebSocket, options = {} }) {
        this.issueTicket = issueTicket;
        this.wsUrl = wsUrl;
        this.WebSocketImpl = WebSocketImpl;
        this.opts = {
            minBackoff: 1000,
            maxBackoff: 30000,
            heartbeatEvery: 25000,
            heartbeatTimeout: 10000,
            requestTimeout: 10000,
            maxTicketFailures: 5,
            ...options,
        };
        this.state = SOCKET_STATES.IDLE;
        this.ws = null;
        this.listeners = new Map();
        this.pending = new Map();
        this.attempt = 0;
        this.ticketFailures = 0;
        this.seq = 0;
        this.timers = { reconnect: null, heartbeat: null, heartbeatTimeout: null };
        this.syncSource = () => [];
    }

    // ---------- eventos ----------

    /** Suscribe a un tipo de frame del servidor ('message.new', …) o a 'state'. Devuelve la función para desuscribir. */
    on(type, fn) {
        if (!this.listeners.has(type)) this.listeners.set(type, new Set());
        this.listeners.get(type).add(fn);
        return () => this.listeners.get(type)?.delete(fn);
    }

    emit(type, payload, frame) {
        this.listeners.get(type)?.forEach((fn) => {
            try {
                fn(payload, frame);
            } catch (e) {
                // Un listener roto no debe tumbar el socket.
                console.error('[chat] listener de', type, 'falló', e);
            }
        });
    }

    setState(state) {
        if (this.state === state) return;
        this.state = state;
        this.emit('state', state);
    }

    /** Qué conversaciones conoce la UI y hasta qué seq: se envía como `sync` en cada (re)conexión. */
    setSyncSource(fn) {
        this.syncSource = fn;
    }

    isOpen() {
        return this.state === SOCKET_STATES.OPEN;
    }

    // ---------- ciclo de vida ----------

    start() {
        if (this.state !== SOCKET_STATES.IDLE && this.state !== SOCKET_STATES.STOPPED) return;
        this.stopped = false;
        this.connect();
    }

    stop() {
        this.stopped = true;
        this.clearTimers();
        this.failPending(new ChatError('CHAT_SOCKET_CLOSED', 'Conexión cerrada'));
        if (this.ws) {
            const ws = this.ws;
            this.ws = null;
            try {
                ws.close(1000, 'bye');
            } catch {
                // ya estaba cerrada
            }
        }
        this.setState(SOCKET_STATES.STOPPED);
    }

    async connect() {
        if (this.stopped) return;
        this.setState(this.attempt === 0 ? SOCKET_STATES.CONNECTING : SOCKET_STATES.RECONNECTING);
        let ticket;
        try {
            ({ ticket } = await this.issueTicket());
        } catch (e) {
            // 401: el token no sirve para el chat (p. ej. aún sin el emisor ES256). Insistir no lo arregla.
            if (e?.status === 401 || e?.status === 403) {
                this.setState(SOCKET_STATES.UNAVAILABLE);
                return;
            }
            this.ticketFailures += 1;
            if (this.ticketFailures >= this.opts.maxTicketFailures) {
                this.setState(SOCKET_STATES.UNAVAILABLE);
            }
            this.scheduleReconnect();
            return;
        }
        if (this.stopped) return;

        const ws = new this.WebSocketImpl(this.wsUrl(ticket));
        this.ws = ws;
        ws.onopen = () => {
            if (ws !== this.ws) return;
            this.setState(SOCKET_STATES.OPEN);
            this.startHeartbeat();
            // El handshake se acepta ANTES de validar el ticket (un ticket inválido llega como cierre 4401 justo
            // después). Solo un primer pong prueba que la sesión está autenticada: recién ahí se reinician el
            // backoff y el contador de fallos, o un ticket rechazado una y otra vez reconectaría sin pausa.
            this.request('ping', {}, this.opts.heartbeatTimeout)
                .then(() => {
                    if (ws !== this.ws) return;
                    this.attempt = 0;
                    this.ticketFailures = 0;
                })
                .catch(() => {});
            this.sendSync();
            this.emit('open');
        };
        ws.onmessage = (event) => {
            if (ws !== this.ws) return;
            this.onFrame(event.data);
        };
        ws.onclose = (event) => {
            if (ws !== this.ws) return;
            this.ws = null;
            this.clearTimers();
            this.failPending(new ChatError('CHAT_SOCKET_CLOSED', 'Se perdió la conexión'));
            if (this.stopped) return;
            if (event.code === CLOSE_SUSPENDED) {
                this.setState(SOCKET_STATES.SUSPENDED);
                return;
            }
            // 4401 (ticket vencido/usado), 1001 (la instancia se apaga), 1006 (red), 1009…: pedir otro ticket y volver.
            if (event.code === CLOSE_INVALID_TICKET) {
                this.ticketFailures += 1;
                if (this.ticketFailures >= this.opts.maxTicketFailures) {
                    this.setState(SOCKET_STATES.UNAVAILABLE);
                    return;
                }
            }
            this.scheduleReconnect();
        };
        ws.onerror = () => {
            // onclose llega siempre después: ahí se decide.
        };
    }

    scheduleReconnect() {
        if (this.stopped) return;
        if (this.state !== SOCKET_STATES.UNAVAILABLE) this.setState(SOCKET_STATES.RECONNECTING);
        const base = Math.min(this.opts.maxBackoff, this.opts.minBackoff * 2 ** this.attempt);
        const delay = base / 2 + Math.random() * (base / 2); // jitter: evita que todos reconecten a la vez
        this.attempt += 1;
        clearTimeout(this.timers.reconnect);
        this.timers.reconnect = setTimeout(() => this.connect(), delay);
    }

    /** Reintento inmediato (p. ej. al volver la red o la pestaña), sin esperar el backoff. */
    reconnectNow() {
        if (this.stopped || this.state === SOCKET_STATES.OPEN || this.state === SOCKET_STATES.SUSPENDED) return;
        clearTimeout(this.timers.reconnect);
        this.attempt = 0;
        this.ticketFailures = 0;
        this.connect();
    }

    clearTimers() {
        clearTimeout(this.timers.reconnect);
        clearInterval(this.timers.heartbeat);
        clearTimeout(this.timers.heartbeatTimeout);
    }

    startHeartbeat() {
        clearInterval(this.timers.heartbeat);
        this.timers.heartbeat = setInterval(() => {
            this.request('ping', {}, this.opts.heartbeatTimeout).catch(() => {
                // Sin pong a tiempo: la conexión está muerta aunque no se haya cerrado. Forzar reconexión.
                if (this.ws) {
                    try {
                        this.ws.close(4000, 'heartbeat timeout');
                    } catch {
                        // nada
                    }
                }
            });
        }, this.opts.heartbeatEvery);
    }

    // ---------- frames ----------

    onFrame(raw) {
        let frame;
        try {
            frame = JSON.parse(raw);
        } catch {
            return;
        }
        const waiting = frame.id != null ? this.pending.get(frame.id) : null;
        if (waiting && (REPLY_TYPES.has(frame.type) || frame.type === 'error')) {
            this.pending.delete(frame.id);
            clearTimeout(waiting.timer);
            if (frame.type === 'error') {
                waiting.reject(new ChatError(frame.payload?.code, frame.payload?.message));
            } else {
                waiting.resolve(frame.payload);
            }
        }
        this.emit(frame.type, frame.payload, frame);
    }

    nextId() {
        this.seq += 1;
        return `f-${Date.now().toString(36)}-${this.seq}`;
    }

    /** Envía un frame sin esperar respuesta. Devuelve false si no hay conexión abierta. */
    send(type, payload) {
        if (!this.ws || this.state !== SOCKET_STATES.OPEN) return false;
        this.ws.send(JSON.stringify({ type, id: this.nextId(), payload }));
        return true;
    }

    /** Envía un frame y espera la respuesta con su mismo id (rechaza con ChatError si es `error`). */
    request(type, payload, timeout = this.opts.requestTimeout) {
        if (!this.ws || this.state !== SOCKET_STATES.OPEN) {
            return Promise.reject(new ChatError('CHAT_SOCKET_CLOSED', 'Sin conexión en tiempo real'));
        }
        const id = this.nextId();
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new ChatError('CHAT_TIMEOUT', 'El servidor no respondió a tiempo'));
            }, timeout);
            this.pending.set(id, { resolve, reject, timer });
            try {
                this.ws.send(JSON.stringify({ type, id, payload }));
            } catch (e) {
                clearTimeout(timer);
                this.pending.delete(id);
                reject(new ChatError('CHAT_SOCKET_CLOSED', e?.message));
            }
        });
    }

    failPending(error) {
        this.pending.forEach(({ reject, timer }) => {
            clearTimeout(timer);
            reject(error);
        });
        this.pending.clear();
    }

    sendSync() {
        let conversations;
        try {
            conversations = this.syncSource() || [];
        } catch {
            conversations = [];
        }
        if (conversations.length === 0) return;
        this.request('sync', { conversations })
            .then((payload) => this.emit('sync', payload))
            .catch(() => {
                // si falla, la UI recarga por REST al recibir 'open'
            });
    }

    // ---------- operaciones del protocolo ----------

    /** Resuelve con el payload del `message.ack` (incluye `warnings` y `duplicate`). */
    sendMessage(conversationId, clientMessageId, body) {
        return this.request('message.send', {
            conversation_id: conversationId,
            client_message_id: clientMessageId,
            body,
        });
    }

    markRead(conversationId, seq) {
        return this.send('conversation.read', { conversation_id: conversationId, seq });
    }
}
