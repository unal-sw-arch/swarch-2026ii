import { chatApi, chatWsUrl } from '../../../shared/api';
import { ChatSocket } from './chatSocket';

export { ChatSocket, ChatError, SOCKET_STATES } from './chatSocket';

/**
 * Una sola conexión WebSocket por pestaña, compartida por todo lo que la necesite (guía §8.2: «una conexión por
 * cliente»). La instancia es perezosa y persistente —los componentes se suscriben a eventos en cualquier orden—;
 * lo que se cuenta son los usos: se conecta con el primero y se cierra con el último.
 */
let instance = null;
let users = 0;

export function getChatSocket() {
    if (!instance) {
        instance = new ChatSocket({ issueTicket: chatApi.issueWsTicket, wsUrl: chatWsUrl });
    }
    return instance;
}

// Al volver la red o la pestaña se reintenta enseguida en vez de esperar el backoff.
const reconnectNow = () => instance?.reconnectNow();
const onVisible = () => {
    if (document.visibilityState === 'visible') reconnectNow();
};

export function acquireChatSocket() {
    const socket = getChatSocket();
    users += 1;
    if (users === 1) {
        window.addEventListener('online', reconnectNow);
        document.addEventListener('visibilitychange', onVisible);
        socket.start();
    }
    return socket;
}

export function releaseChatSocket() {
    users = Math.max(0, users - 1);
    if (users === 0 && instance) {
        window.removeEventListener('online', reconnectNow);
        document.removeEventListener('visibilitychange', onVisible);
        instance.stop();
    }
}
