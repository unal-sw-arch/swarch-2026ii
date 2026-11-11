import { useEffect, useRef, useSyncExternalStore } from 'react';
import { acquireChatSocket, getChatSocket, releaseChatSocket } from '../realtime';

const subscribeState = (onChange) => getChatSocket().on('state', onChange);
const readState = () => getChatSocket().state;

/** Mantiene abierta la conexión mientras el componente esté montado y devuelve su estado. */
export function useChatConnection() {
    useEffect(() => {
        acquireChatSocket();
        return releaseChatSocket;
    }, []);
    return useSyncExternalStore(subscribeState, readState);
}

/** Escucha un tipo de frame del servidor ('message.new', 'sync', 'open'…) con el handler más reciente. */
export function useChatEvent(type, handler) {
    const latest = useRef(handler);
    useEffect(() => {
        latest.current = handler;
    });
    useEffect(() => getChatSocket().on(type, (payload, frame) => latest.current(payload, frame)), [type]);
}
