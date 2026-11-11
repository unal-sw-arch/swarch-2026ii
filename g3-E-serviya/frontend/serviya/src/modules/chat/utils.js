import { STATUS_MAP } from '../requests/utils';

export { STATUS_MAP as REQUEST_STATUS_MAP };

/** Mensaje en una forma única, venga del WebSocket (message_id) o de REST (id). */
export function normalizeMessage(m) {
    return {
        id: m.message_id ?? m.id,
        conversationId: m.conversation_id,
        seq: m.seq,
        senderId: m.sender_id ?? null,
        kind: m.kind || 'TEXT',
        body: m.body,
        flags: m.flags || [],
        createdAt: m.created_at,
    };
}

/** Une mensajes por id y los ordena por seq (el orden total del servidor, igual para ambos participantes). */
export function mergeMessages(current, incoming) {
    const byId = new Map(current.map((m) => [m.id, m]));
    incoming.forEach((m) => byId.set(m.id, m));
    return [...byId.values()].sort((a, b) => a.seq - b.seq);
}

/** Token de idempotencia del mensaje: se reutiliza en cada reintento (el servidor no lo duplica). */
export function newClientMessageId() {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function sameDay(a, b) {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export function formatTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' });
}

/** «Hoy», «Ayer» o «12 nov» (con año si no es el actual): separador de días en la conversación. */
export function formatDayLabel(iso) {
    const d = new Date(iso);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (sameDay(d, today)) return 'Hoy';
    if (sameDay(d, yesterday)) return 'Ayer';
    const label = `${d.getDate()} ${MONTHS[d.getMonth()]}`;
    return d.getFullYear() === today.getFullYear() ? label : `${label} ${d.getFullYear()}`;
}

export function isSameDay(isoA, isoB) {
    return sameDay(new Date(isoA), new Date(isoB));
}

/** Hora si es de hoy, «Ayer» o la fecha corta: para el listado de conversaciones. */
export function formatListTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (sameDay(d, new Date())) return formatTime(iso);
    return formatDayLabel(iso);
}

/** Textos para los códigos de error estables del chat (los que no traen ya un mensaje claro del servidor). */
export function chatErrorMessage(error) {
    if (error?.status === 401) return 'El chat no está disponible en este momento.';
    switch (error?.code) {
        case 'CHAT_RATE_LIMITED':
            return 'Estás enviando mensajes muy rápido. Espera unos segundos.';
        case 'CHAT_CONVERSATION_READ_ONLY':
            return 'La conversación es de solo lectura: ya no se pueden enviar mensajes.';
        case 'CHAT_USER_SUSPENDED':
            return 'Tu cuenta no puede enviar mensajes en este momento.';
        case 'CHAT_NOT_PARTICIPANT':
            return 'No participas en esta conversación.';
        case 'CHAT_MESSAGE_TOO_LONG':
            return 'El mensaje es demasiado largo (máximo 2000 caracteres).';
        default:
            return error?.message || 'No se pudo enviar el mensaje.';
    }
}

export const MAX_MESSAGE_LENGTH = 2000;
