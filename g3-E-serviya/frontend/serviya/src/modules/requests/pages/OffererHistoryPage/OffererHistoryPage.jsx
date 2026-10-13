import { useState, useEffect } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, OFFERER_NAV, requestApi } from '../../../../shared';
import { STATUS_MAP, formatDate, getInitials, formatPrice } from '../../utils';

const TABS = [
    { key: '', label: 'Todos' },
    { key: 'COMPLETED', label: 'Completados' },
    { key: 'CANCELLED', label: 'Cancelados' },
    { key: 'NOT_PROVIDED', label: 'No prestados' },
    { key: 'RESCHEDULED', label: 'Reprogramados' },
    { key: 'REJECTED', label: 'Rechazados' },
];

export function OffererHistoryPage() {
    const navigate = useNavigate();
    const [tab, setTab] = useState(0);
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);

    const fetchHistory = (page = 0) => {
        setLoading(true);
        const statusKey = TABS[tab].key;
        const params = { page, size: 20 };
        if (statusKey) {
            params.statuses = statusKey;
        }
        requestApi.getOffererRequests(params)
            .then(data => {
                setRequests(data.content || []);
                setTotalPages(data.totalPages || 1);
                setCurrentPage(data.number || 0);
            })
            .catch(() => {})
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchHistory(0);
    }, [tab]);

    return (
        <DashboardLayout sections={OFFERER_NAV} avatar="CM">
            <div className="ph"><h1>Historial de solicitudes recibidas</h1><p>Registro completo de tu actividad como oferente</p></div>

            <div className="filter-chips">
                {TABS.map((t, i) => (
                    <div key={t.key} className={`chip ${tab === i ? 'active' : ''}`} onClick={() => setTab(i)}>
                        {t.label}
                    </div>
                ))}
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando historial...</div>
            ) : requests.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>
                    <Icon name="history" size={32} />
                    <p style={{ marginTop: '8px' }}>No hay servicios en esta categoría</p>
                </div>
            ) : (
                <>
                    <div className="tbl-wrap">
                        <table>
                            <thead><tr><th>Servicio</th><th>Cliente</th><th>Fecha</th><th>Estado</th><th>Precio</th><th>Acciones</th></tr></thead>
                            <tbody>
                                {requests.map((r) => {
                                    const st = STATUS_MAP[r.status] || { label: r.status, badge: '' };
                                    return (
                                        <tr key={r.requestId}>
                                            <td><strong>{r.serviceTitle}</strong></td>
                                            <td>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                                                    <div className="av av-xs">{getInitials(r.counterpartyName)}</div>
                                                    {r.counterpartyName}
                                                </div>
                                            </td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{formatDate(r.scheduledDate || r.createdAt)}</td>
                                            <td><span className={`badge ${st.badge}`}>{st.label}</span></td>
                                            <td style={r.requestedPrice != null ? { fontWeight: 600, color: 'var(--c-primary-d)' } : { color: 'var(--c-soft)' }}>{r.requestedPrice != null ? formatPrice(r.requestedPrice) : '—'}</td>
                                            <td>
                                                <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate(`/requests/${r.requestId}`, { state: { as: 'offerer' } })}>
                                                    Ver
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    {totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginTop: '16px' }}>
                            <button className="btn btn-ghost btn-sm" disabled={currentPage === 0} onClick={() => fetchHistory(currentPage - 1)}>Anterior</button>
                            <span style={{ fontSize: '13px', color: 'var(--c-mid)', alignSelf: 'center' }}>Página {currentPage + 1} de {totalPages}</span>
                            <button className="btn btn-ghost btn-sm" disabled={currentPage >= totalPages - 1} onClick={() => fetchHistory(currentPage + 1)}>Siguiente</button>
                        </div>
                    )}
                </>
            )}
        </DashboardLayout>
    );
}

export default OffererHistoryPage;
