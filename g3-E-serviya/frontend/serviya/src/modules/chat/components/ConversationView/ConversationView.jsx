import { Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon, chatApi } from '../../../../shared';
import { getInitials } from '../../../requests/utils';
import { getChatSocket } from '../../realtime';
import { useChatEvent } from '../../hooks/useChatSocket';
import {
    MAX_MESSAGE_LENGTH, REQUEST_STATUS_MAP, chatErrorMessage, formatDayLabel, formatTime, isSameDay,
    mergeMessages, newClientMessageId, normalizeMessage,
} from '../../utils';

const PAGE_SIZE = 50;

const maxSeq = (messages) => (messages.length ? messages[messages.length - 1].seq : 0);

/**
 * Una conversación: historial, envío y confirmaciones de lectura.
 * Se monta con `key={conversationId}`: cambiar de conversación arranca de cero (sin estado arrastrado).
 */
export function ConversationView({ conversationId, me, summary, connection, onRead, onActivity, onBack }) {
    const navigate = useNavigate();
    const [detail, setDetail] = useState(null);
    const [messages, setMessages] = useState([]);
    const [pending, setPending] = useState([]);
    const [loadState, setLoadState] = useState('loading');
    const [loadError, setLoadError] = useState(null);
    const [hasOlder, setHasOlder] = useState(false);
    const [loadingOlder, setLoadingOlder] = useState(false);
    const [otherReadSeq, setOtherReadSeq] = useState(0);
    const [text, setText] = useState('');
    const [contactWarning, setContactWarning] = useState(false);

    const scrollRef = useRef(null);
    const stickToBottom = useRef(true);
    const restoreFromBottom = useRef(null);
    const lastReadSent = useRef(0);
    const messagesRef = useRef(messages);
    const pendingRef = useRef(pending);
    useEffect(() => {
        messagesRef.current = messages;
        pendingRef.current = pending;
    });

    // ---------- carga inicial ----------
    useEffect(() => {
        let cancelled = false;
        Promise.all([chatApi.getConversation(conversationId), chatApi.getMessages(conversationId, { limit: PAGE_SIZE })])
            .then(([conv, page]) => {
                if (cancelled) return;
                const items = (page.items || []).map(normalizeMessage);
                const other = conv.participants.find((p) => p.user_id !== me);
                lastReadSent.current = conv.my_last_read_seq;
                setDetail(conv);
                setMessages(items);
                setHasOlder(items.length === PAGE_SIZE && items[0].seq > 1);
                setOtherReadSeq(other?.last_read_seq ?? 0);
                setLoadState('ready');
            })
            .catch((e) => {
                if (cancelled) return;
                setLoadError(e.status === 403 ? 'No participas en esta conversación.'
                    : e.status === 404 ? 'Esta conversación no existe.'
                        : e.status === 401 ? 'El chat no está disponible en este momento.'
                            : 'No se pudo cargar la conversación.');
                setLoadState('error');
            });
        return () => {
            cancelled = true;
        };
    }, [conversationId, me]);

    // Tras reconectar, el socket pide lo que llegó después del último mensaje que tenemos (RNF-CHAT-16).
    useEffect(() => {
        if (loadState !== 'ready') return undefined;
        const socket = getChatSocket();
        socket.setSyncSource(() => [{ conversation_id: conversationId, last_seq: maxSeq(messagesRef.current) }]);
        return () => socket.setSyncSource(() => []);
    }, [conversationId, loadState]);

    // ---------- lectura ----------
    const markReadIfNeeded = useCallback(() => {
        const seq = maxSeq(messagesRef.current);
        if (seq <= lastReadSent.current || document.visibilityState !== 'visible') return;
        lastReadSent.current = seq;
        const socket = getChatSocket();
        if (!socket.markRead(conversationId, seq)) {
            chatApi.markRead(conversationId, seq).catch(() => {
                lastReadSent.current = 0; // se reintenta en el próximo cambio
            });
        }
        onRead(conversationId);
    }, [conversationId, onRead]);

    useEffect(() => {
        if (loadState === 'ready') markReadIfNeeded();
    }, [loadState, messages, markReadIfNeeded]);

    useEffect(() => {
        document.addEventListener('visibilitychange', markReadIfNeeded);
        return () => document.removeEventListener('visibilitychange', markReadIfNeeded);
    }, [markReadIfNeeded]);

    // ---------- tiempo real ----------
    useChatEvent('message.new', (payload) => {
        if (payload.conversation_id !== conversationId) return;
        setMessages((prev) => mergeMessages(prev, [normalizeMessage(payload)]));
    });
    useChatEvent('conversation.read_updated', (payload) => {
        if (payload.conversation_id !== conversationId || payload.user_id === me) return;
        setOtherReadSeq((s) => Math.max(s, payload.seq));
    });
    useChatEvent('conversation.status_changed', (payload) => {
        if (payload.conversation_id !== conversationId) return;
        setDetail((d) => (d ? { ...d, status: payload.status, context_status: payload.context_status } : d));
    });
    useChatEvent('sync', (payload) => {
        const entry = payload?.conversations?.find((c) => c.conversation_id === conversationId);
        if (entry) setMessages((prev) => mergeMessages(prev, entry.messages.map(normalizeMessage)));
    });

    // ---------- envío ----------
    const deliver = useCallback(async (entry) => {
        setPending((prev) => prev.map((p) => (p.clientId === entry.clientId ? { ...p, status: 'sending', error: null } : p)));
        try {
            let message = null;
            let warnings = [];
            const socket = getChatSocket();
            if (socket.isOpen()) {
                try {
                    const ack = await socket.sendMessage(conversationId, entry.clientId, entry.body);
                    message = {
                        id: ack.message_id, conversationId, seq: ack.seq, senderId: me, kind: 'TEXT',
                        body: entry.body.trim(), flags: ack.warnings || [], createdAt: ack.created_at,
                    };
                    warnings = ack.warnings || [];
                } catch (e) {
                    // Se cayó la conexión a mitad: se reintenta por REST con el MISMO client_message_id (sin duplicar).
                    if (e.code !== 'CHAT_SOCKET_CLOSED' && e.code !== 'CHAT_TIMEOUT') throw e;
                }
            }
            if (!message) {
                const res = await chatApi.sendMessage(conversationId, entry.clientId, entry.body);
                message = normalizeMessage(res.message);
                warnings = res.warnings || [];
            }
            stickToBottom.current = true;
            setPending((prev) => prev.filter((p) => p.clientId !== entry.clientId));
            setMessages((prev) => mergeMessages(prev, [message]));
            if (warnings.includes('CONTACT_INFO')) setContactWarning(true);
            onActivity(conversationId, message);
        } catch (e) {
            const retryable = !e.status && !e.code; // error de red: vale la pena reintentar solo
            setPending((prev) => prev.map((p) => (p.clientId === entry.clientId
                ? { ...p, status: 'failed', error: chatErrorMessage(e), retryable } : p)));
            if (e.code === 'CHAT_CONVERSATION_READ_ONLY') {
                setDetail((d) => (d ? { ...d, status: 'READ_ONLY' } : d));
            }
        }
    }, [conversationId, me, onActivity]);

    // Al reconectar, se reintentan solos los que fallaron por red.
    useChatEvent('open', () => {
        pendingRef.current.filter((p) => p.status === 'failed' && p.retryable).forEach(deliver);
    });

    const submit = () => {
        const body = text.trim();
        if (!body || body.length > MAX_MESSAGE_LENGTH) return;
        const entry = { clientId: newClientMessageId(), body, status: 'sending', createdAt: new Date().toISOString() };
        stickToBottom.current = true;
        setPending((prev) => [...prev, entry]);
        setText('');
        deliver(entry);
    };

    const onKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            submit();
        }
    };

    const discard = (clientId) => setPending((prev) => prev.filter((p) => p.clientId !== clientId));

    // ---------- historial hacia atrás ----------
    const loadOlder = () => {
        if (!messages.length || loadingOlder) return;
        setLoadingOlder(true);
        chatApi.getMessages(conversationId, { before_seq: messages[0].seq, limit: PAGE_SIZE })
            .then((page) => {
                const older = (page.items || []).map(normalizeMessage);
                const el = scrollRef.current;
                if (el) restoreFromBottom.current = el.scrollHeight - el.scrollTop;
                setMessages((prev) => mergeMessages(prev, older));
                setHasOlder(older.length === PAGE_SIZE && older[0].seq > 1);
            })
            .catch(() => {})
            .finally(() => setLoadingOlder(false));
    };

    // ---------- scroll ----------
    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        if (restoreFromBottom.current != null) {
            el.scrollTop = el.scrollHeight - restoreFromBottom.current; // conservar la posición al anteponer
            restoreFromBottom.current = null;
        } else if (stickToBottom.current) {
            el.scrollTop = el.scrollHeight;
        }
    }, [messages, pending, loadState]);

    const onScroll = () => {
        const el = scrollRef.current;
        if (el) stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    };

    // ---------- render ----------
    if (loadState !== 'ready') {
        return (
            <section className="chat-view card">
                {onBack && (
                    <div className="chat-head">
                        <button type="button" className="btn btn-ghost btn-sm" onClick={onBack}><Icon name="chevronLeft" size={14} />Volver</button>
                    </div>
                )}
                <div className="chat-empty">{loadState === 'loading' ? 'Cargando conversación...' : loadError}</div>
            </section>
        );
    }

    const meParticipant = detail.participants.find((p) => p.user_id === me);
    const other = detail.participants.find((p) => p.user_id !== me);
    const viewerAs = meParticipant?.role === 'OFFERER' ? 'offerer' : 'client';
    const otherLabel = other?.role === 'OFFERER' ? 'Oferente' : 'Cliente';
    const name = summary?.counterpart?.display_name || otherLabel;
    const st = REQUEST_STATUS_MAP[detail.context_status];
    const readOnly = detail.status !== 'ACTIVE';
    const tooLong = text.length > MAX_MESSAGE_LENGTH;

    const rows = [
        ...messages.map((m) => ({ key: `m-${m.id}`, at: m.createdAt, message: m })),
        ...pending.map((p) => ({ key: `p-${p.clientId}`, at: p.createdAt, pending: p })),
    ];

    return (
        <section className="chat-view card">
            <div className="chat-head">
                {onBack && (
                    <button type="button" className="btn btn-ghost btn-sm chat-back" onClick={onBack} aria-label="Volver">
                        <Icon name="chevronLeft" size={16} />
                    </button>
                )}
                <div className="av av-sm">{getInitials(name)}</div>
                <div className="chat-head-info">
                    <div className="chat-head-name">{name}</div>
                    <div className="chat-head-sub">
                        {otherLabel} · Solicitud #{detail.context_id}
                        {st && <span className={`badge ${st.badge}`}>{st.label}</span>}
                    </div>
                </div>
                {detail.context_id && (
                    <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        onClick={() => navigate(`/requests/${detail.context_id}`, { state: { as: viewerAs } })}
                    >
                        Ver solicitud
                    </button>
                )}
            </div>

            <div className="chat-messages" ref={scrollRef} onScroll={onScroll}>
                {hasOlder && (
                    <button type="button" className="btn btn-ghost btn-sm chat-older" disabled={loadingOlder} onClick={loadOlder}>
                        {loadingOlder ? 'Cargando...' : 'Cargar mensajes anteriores'}
                    </button>
                )}
                {rows.length === 0 && (
                    <div className="chat-empty">
                        <Icon name="chat" size={28} />
                        <p>{readOnly ? 'Esta conversación no tuvo mensajes.' : `Escríbele a ${name} para coordinar los detalles del servicio.`}</p>
                    </div>
                )}
                {rows.map((row, i) => {
                    const prev = rows[i - 1];
                    const showDay = !prev || !isSameDay(prev.at, row.at);
                    return (
                        <Fragment key={row.key}>
                            {showDay && <div className="chat-day"><span>{formatDayLabel(row.at)}</span></div>}
                            {row.message
                                ? <MessageRow message={row.message} mine={row.message.senderId === me} read={row.message.seq <= otherReadSeq} />
                                : <PendingRow entry={row.pending} onRetry={() => deliver(row.pending)} onDiscard={() => discard(row.pending.clientId)} />}
                        </Fragment>
                    );
                })}
            </div>

            {contactWarning && (
                <div className="chat-warning">
                    <Icon name="shield" size={16} />
                    <div>
                        <strong>Tu mensaje se envió.</strong> Parece incluir un teléfono, correo o enlace. Por tu seguridad,
                        coordina y paga dentro de ServiYa: fuera de la plataforma no podemos ayudarte si algo sale mal.
                    </div>
                    <button type="button" className="chat-warning-close" onClick={() => setContactWarning(false)} aria-label="Cerrar">
                        <Icon name="close" size={14} />
                    </button>
                </div>
            )}

            {readOnly ? (
                <div className="chat-readonly">
                    <Icon name="lock" size={15} />
                    Conversación de solo lectura{st ? `: la solicitud está ${st.label.toLowerCase()}` : ''}. Puedes consultar el historial.
                </div>
            ) : (
                <div className="chat-composer">
                    {connection !== 'open' && (
                        <div className="chat-offline">
                            <Icon name="info" size={13} />
                            {connection === 'unavailable'
                                ? 'Sin tiempo real: los mensajes se envían igual, pero no verás los nuevos hasta recargar.'
                                : 'Reconectando… tus mensajes se envían igual.'}
                        </div>
                    )}
                    <div className="chat-composer-row">
                        <textarea
                            className="input chat-input"
                            rows={1}
                            placeholder="Escribe un mensaje..."
                            value={text}
                            onChange={(e) => setText(e.target.value)}
                            onKeyDown={onKeyDown}
                        />
                        <button type="button" className="btn btn-primary chat-send" disabled={!text.trim() || tooLong} onClick={submit} aria-label="Enviar">
                            <Icon name="send" size={16} />
                        </button>
                    </div>
                    {text.length > MAX_MESSAGE_LENGTH - 200 && (
                        <div className={`chat-counter ${tooLong ? 'over' : ''}`}>{text.length}/{MAX_MESSAGE_LENGTH}</div>
                    )}
                </div>
            )}
        </section>
    );
}

function MessageRow({ message, mine, read }) {
    if (message.kind === 'SYSTEM') {
        return <div className="chat-system">{message.body}</div>;
    }
    return (
        <div className={`chat-msg ${mine ? 'mine' : 'theirs'}`}>
            {/* Texto plano: React lo escapa, nunca se interpreta como HTML. */}
            <div className="chat-bubble">{message.body}</div>
            <div className="chat-meta">
                {formatTime(message.createdAt)}
                {mine && (read
                    ? <span className="chat-status read"><Icon name="checkCircle" size={12} />Leído</span>
                    : <span className="chat-status"><Icon name="check" size={12} />Enviado</span>)}
            </div>
        </div>
    );
}

function PendingRow({ entry, onRetry, onDiscard }) {
    const failed = entry.status === 'failed';
    return (
        <div className={`chat-msg mine ${failed ? 'failed' : 'sending'}`}>
            <div className="chat-bubble">{entry.body}</div>
            <div className="chat-meta">
                {failed ? (
                    <span className="chat-status error">
                        <Icon name="alertCircle" size={12} />{entry.error || 'No enviado'}
                        <button type="button" className="chat-link" onClick={onRetry}>Reintentar</button>
                        <button type="button" className="chat-link" onClick={onDiscard}>Descartar</button>
                    </span>
                ) : (
                    <span className="chat-status"><Icon name="clock" size={12} />Enviando…</span>
                )}
            </div>
        </div>
    );
}
