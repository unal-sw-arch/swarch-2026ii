import { Icon } from '../../../../shared';
import { getInitials } from '../../../requests/utils';
import { REQUEST_STATUS_MAP, formatListTime } from '../../utils';

/** Pestañas por estado de la conversación (RF-CHAT-31). */
const STATUS_TABS = [
    { label: 'Todas', value: null },
    { label: 'Activas', value: 'ACTIVE' },
    { label: 'Solo lectura', value: 'READ_ONLY' },
];

/** Estados de la solicitud que se pueden combinar como filtro. */
const REQUEST_FILTERS = ['PENDING', 'ACCEPTED', 'PRESUMABLY_COMPLETED', 'COMPLETED', 'CANCELLED', 'REJECTED'];

function preview(item, me) {
    const last = item.last_message;
    if (!last) return 'Aún no hay mensajes';
    if (last.kind === 'SYSTEM') return last.body;
    return last.sender_id === me ? `Tú: ${last.body}` : last.body;
}

export function ConversationList({
    items, me, loading, error, selectedId, onSelect,
    status, onStatusChange, requestStatuses, onToggleRequestStatus,
    hasMore, loadingMore, onLoadMore,
}) {
    return (
        <aside className="chat-list card">
            <div className="tabs chat-tabs">
                {STATUS_TABS.map((t) => (
                    <div key={t.label} className={`tab ${status === t.value ? 'active' : ''}`} onClick={() => onStatusChange(t.value)}>
                        {t.label}
                    </div>
                ))}
            </div>
            <div className="chat-filters">
                {REQUEST_FILTERS.map((s) => (
                    <button
                        key={s}
                        type="button"
                        className={`chip ${requestStatuses.includes(s) ? 'active' : ''}`}
                        onClick={() => onToggleRequestStatus(s)}
                    >
                        {REQUEST_STATUS_MAP[s]?.label ?? s}
                    </button>
                ))}
            </div>

            <div className="chat-list-items">
                {loading ? (
                    <p className="chat-empty">Cargando conversaciones...</p>
                ) : error ? (
                    <p className="chat-empty">{error}</p>
                ) : items.length === 0 ? (
                    <div className="chat-empty">
                        <Icon name="chat" size={28} />
                        <p>{status || requestStatuses.length ? 'No hay conversaciones con estos filtros.' : 'Aún no tienes conversaciones. Se crean al hacer una solicitud.'}</p>
                    </div>
                ) : (
                    items.map((c) => {
                        const st = REQUEST_STATUS_MAP[c.context_status];
                        const unread = c.unread_count > 0;
                        return (
                            <button
                                type="button"
                                key={c.id}
                                className={`chat-item ${selectedId === c.id ? 'active' : ''} ${unread ? 'unread' : ''}`}
                                onClick={() => onSelect(c.id)}
                            >
                                <div className="av av-md">{getInitials(c.counterpart?.display_name)}</div>
                                <div className="chat-item-body">
                                    <div className="chat-item-top">
                                        <span className="chat-item-name">{c.counterpart?.display_name || 'Conversación'}</span>
                                        <span className="chat-item-time">{formatListTime(c.last_message_at)}</span>
                                    </div>
                                    <div className="chat-item-bottom">
                                        <span className="chat-item-preview">
                                            {c.status === 'READ_ONLY' && <Icon name="lock" size={11} />}
                                            {preview(c, me)}
                                        </span>
                                        {unread && <span className="chat-unread">{c.unread_count > 99 ? '99+' : c.unread_count}</span>}
                                    </div>
                                    {st && <span className={`badge ${st.badge} chat-item-badge`}>{st.label}</span>}
                                </div>
                            </button>
                        );
                    })
                )}
                {hasMore && !loading && (
                    <button type="button" className="btn btn-ghost btn-sm btn-full" disabled={loadingMore} onClick={onLoadMore}>
                        {loadingMore ? 'Cargando...' : 'Cargar más'}
                    </button>
                )}
            </div>
        </aside>
    );
}
