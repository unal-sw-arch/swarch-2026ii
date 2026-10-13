import { useState, useEffect } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, OFFERER_NAV, requestApi } from '../../../../shared';
import { STATUS_MAP, formatDate, getInitials, formatPrice } from '../../utils';

// Vista de solicitudes RECIBIDAS del oferente. Por defecto muestra las activas (no terminales);
// también permite filtrar por estado concreto. Paginada (el backend ya devuelve Page).
const FILTERS = [
    { key: ['PENDING', 'ACCEPTED', 'PRESUMABLY_COMPLETED'], label: 'Activas' },
    { key: ['PENDING'], label: 'Pendientes' },
    { key: ['ACCEPTED'], label: 'Aceptadas' },
    { key: ['PRESUMABLY_COMPLETED'], label: 'Por confirmar' },
];

export function OffererRequestsPage() {
    const navigate = useNavigate();
    const [tab, setTab] = useState(0);
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [total, setTotal] = useState(0);

    const fetchRequests = (page = 0) => {
        setLoading(true);
        requestApi.getOffererRequests({ statuses: FILTERS[tab].key, page, size: 10 })
            .then(data => {
                setRequests(data.content || []);
                setTotalPages(data.totalPages || 1);
                setCurrentPage(data.number || 0);
                setTotal(data.totalElements ?? (data.content || []).length);
            })
            .catch(() => { })
            .finally(() => setLoading(false));
    };

    useEffect(() => { fetchRequests(0); }, [tab]);

    return (
        <DashboardLayout sections={OFFERER_NAV}>
            <div className="ph">
                <h1>Solicitudes recibidas</h1>
                <p>{loading ? 'Cargando…' : `${total} solicitud(es)`}</p>
            </div>

            <div className="filter-chips">
                {FILTERS.map((f, i) => (
                    <div key={f.label} className={`chip ${tab === i ? 'active' : ''}`} onClick={() => setTab(i)}>
                        {f.label}
                    </div>
                ))}
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando solicitudes...</div>
            ) : requests.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>
                    <Icon name="tasks" size={32} />
                    <p style={{ marginTop: '8px' }}>No hay solicitudes en esta categoría</p>
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
                                                    Ver detalle
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
                            <button className="btn btn-ghost btn-sm" disabled={currentPage === 0} onClick={() => fetchRequests(currentPage - 1)}>Anterior</button>
                            <span style={{ fontSize: '13px', color: 'var(--c-mid)', alignSelf: 'center' }}>Página {currentPage + 1} de {totalPages}</span>
                            <button className="btn btn-ghost btn-sm" disabled={currentPage >= totalPages - 1} onClick={() => fetchRequests(currentPage + 1)}>Siguiente</button>
                        </div>
                    )}
                </>
            )}
        </DashboardLayout>
    );
}

export default OffererRequestsPage;
