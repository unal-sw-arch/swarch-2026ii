import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { DashboardLayout, Icon, CLIENT_NAV, OFFERER_NAV, chatApi, rolesFromToken, userIdFromToken } from '../../../../shared';
import { ConversationList } from '../../components/ConversationList/ConversationList';
import { ConversationView } from '../../components/ConversationView/ConversationView';
import { useChatConnection, useChatEvent } from '../../hooks/useChatSocket';

import './MessagesPage.css';

const LIST_SIZE = 20;

const CONNECTION_LABEL = {
    open: { text: 'En línea', cls: 'online' },
    connecting: { text: 'Conectando…', cls: 'pending' },
    reconnecting: { text: 'Reconectando…', cls: 'pending' },
    unavailable: { text: 'Sin tiempo real', cls: 'off' },
    suspended: { text: 'Cuenta suspendida', cls: 'off' },
};

/** Chat 1 a 1 cliente–oferente (fase 1): listado con filtros a la izquierda y la conversación a la derecha. */
export function MessagesPage() {
    const { conversationId } = useParams();
    const selectedId = conversationId ? Number(conversationId) : null;
    const navigate = useNavigate();
    const location = useLocation();
    const me = userIdFromToken();
    const connection = useChatConnection();

    // Mismo criterio que el resto de páginas compartidas: el rol desde el que se llegó, o el del token.
    const as = location.state?.as;
    const sections = as === 'offerer' || (!as && rolesFromToken().includes('OFFERER')) ? OFFERER_NAV : CLIENT_NAV;

    const [items, setItems] = useState([]);
    const [nextCursor, setNextCursor] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState(null);
    const [unavailable, setUnavailable] = useState(false);
    const [status, setStatus] = useState(null);
    const [requestStatuses, setRequestStatuses] = useState([]);
    const [reloadToken, setReloadToken] = useState(0);

    const query = useCallback((cursor) => chatApi.listConversations({
        status: status ? [status] : undefined,
        request_status: requestStatuses.length ? requestStatuses : undefined,
        cursor,
        limit: LIST_SIZE,
    }), [status, requestStatuses]);

    useEffect(() => {
        let cancelled = false;
        query(null)
            .then((page) => {
                if (cancelled) return;
                setItems(page.items || []);
                setNextCursor(page.next_cursor);
                setError(null);
                setUnavailable(false);
            })
            .catch((e) => {
                if (cancelled) return;
                if (e.status === 401) setUnavailable(true);
                else setError('No se pudieron cargar las conversaciones.');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [query, reloadToken]);

    const reload = useCallback(() => setReloadToken((t) => t + 1), []);

    const changeStatus = (value) => {
        if (value === status) return;
        setLoading(true);
        setStatus(value);
    };
    const toggleRequestStatus = (value) => {
        setLoading(true);
        setRequestStatuses((prev) => (prev.includes(value) ? prev.filter((s) => s !== value) : [...prev, value]));
    };
    const loadMore = () => {
        if (!nextCursor) return;
        setLoadingMore(true);
        query(nextCursor)
            .then((page) => {
                setItems((prev) => [...prev, ...(page.items || []).filter((c) => !prev.some((p) => p.id === c.id))]);
                setNextCursor(page.next_cursor);
            })
            .catch(() => {})
            .finally(() => setLoadingMore(false));
    };

    const select = (id) => navigate(`/messages/${id}`, { state: location.state });
    const back = () => navigate('/messages', { state: location.state });

    // ---------- tiempo real sobre el listado ----------
    const bump = useCallback((conversationIdToUpdate, patch) => {
        setItems((prev) => {
            const idx = prev.findIndex((c) => c.id === conversationIdToUpdate);
            if (idx === -1) return prev;
            const updated = patch(prev[idx]);
            return [updated, ...prev.slice(0, idx), ...prev.slice(idx + 1)];
        });
    }, []);

    useChatEvent('message.new', (m) => {
        if (!items.some((c) => c.id === m.conversation_id)) {
            reload(); // conversación aún no cargada (o fuera de la página actual)
            return;
        }
        bump(m.conversation_id, (c) => ({
            ...c,
            last_message: { sender_id: m.sender_id, kind: m.kind, body: m.body },
            last_message_at: m.created_at,
            unread_count: m.sender_id === me ? c.unread_count : c.unread_count + 1,
        }));
    });
    useChatEvent('conversation.created', reload);
    useChatEvent('conversation.status_changed', (p) => {
        setItems((prev) => prev.map((c) => (c.id === p.conversation_id
            ? { ...c, status: p.status, context_status: p.context_status } : c)));
    });
    useChatEvent('conversation.read_updated', (p) => {
        if (p.user_id !== me) return;
        setItems((prev) => prev.map((c) => (c.id === p.conversation_id ? { ...c, unread_count: 0 } : c)));
    });
    // Tras una reconexión pudo haber cambios que no llegaron: se recarga el listado (no en la primera conexión).
    const connectedOnce = useRef(false);
    useChatEvent('open', () => {
        if (connectedOnce.current) reload();
        connectedOnce.current = true;
    });

    const onRead = useCallback((id) => {
        setItems((prev) => prev.map((c) => (c.id === id && c.unread_count ? { ...c, unread_count: 0 } : c)));
    }, []);
    const onActivity = useCallback((id, message) => {
        bump(id, (c) => ({
            ...c,
            last_message: { sender_id: message.senderId, kind: message.kind, body: message.body },
            last_message_at: message.createdAt,
        }));
    }, [bump]);

    const summary = items.find((c) => c.id === selectedId);
    const conn = CONNECTION_LABEL[connection];

    return (
        <DashboardLayout sections={sections}>
            <div className="chat-page-head">
                <div className="ph" style={{ margin: 0 }}>
                    <h1>Mensajes</h1>
                    <p>Coordina los detalles de tus solicitudes sin salir de ServiYa.</p>
                </div>
                {conn && !unavailable && <span className={`chat-conn ${conn.cls}`}><span className="chat-conn-dot" />{conn.text}</span>}
            </div>

            {unavailable ? (
                <div className="card chat-unavailable">
                    <Icon name="chat" size={36} />
                    <p><strong>El chat no está disponible en este momento.</strong></p>
                    <p>Estamos terminando de habilitarlo para tu cuenta. Mientras tanto, puedes gestionar tus solicitudes como siempre.</p>
                </div>
            ) : (
                <div className={`chat-shell ${selectedId ? 'has-selection' : ''}`}>
                    <ConversationList
                        items={items}
                        me={me}
                        loading={loading}
                        error={error}
                        selectedId={selectedId}
                        onSelect={select}
                        status={status}
                        onStatusChange={changeStatus}
                        requestStatuses={requestStatuses}
                        onToggleRequestStatus={toggleRequestStatus}
                        hasMore={!!nextCursor}
                        loadingMore={loadingMore}
                        onLoadMore={loadMore}
                    />
                    {selectedId ? (
                        <ConversationView
                            key={selectedId}
                            conversationId={selectedId}
                            me={me}
                            summary={summary}
                            connection={connection}
                            onRead={onRead}
                            onActivity={onActivity}
                            onBack={back}
                        />
                    ) : (
                        <section className="chat-view card chat-placeholder">
                            <Icon name="chat" size={40} />
                            <p>Selecciona una conversación para ver los mensajes.</p>
                        </section>
                    )}
                </div>
            )}
        </DashboardLayout>
    );
}

export default MessagesPage;
