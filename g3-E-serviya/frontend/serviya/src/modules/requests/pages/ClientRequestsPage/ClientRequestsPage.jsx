import { useState, useEffect } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, ToastContainer, useToast, CLIENT_NAV, reportApi, requestApi, feedbackApi } from '../../../../shared';
import { ReviewModal } from '../../components/ReviewModal/ReviewModal';
import { STATUS_MAP, formatDate, timeAgo, getInitials, formatPrice, isTerminal } from '../../utils';

import './ClientRequestsPage.css';

const FILTERS = [
    { key: '',        label: 'Todas' },
    { key: 'PENDING', label: 'Pendientes' },
    { key: 'ACCEPTED', label: 'Aceptadas' },
];

export function ClientRequestsPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [filter, setFilter] = useState(0);
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [reschedOpen, setReschedOpen] = useState(false);
    const [reportOpen, setReportOpen] = useState(false);
    // RF-073: solicitud concreta que se esta reportando (antes iba fija a la #1).
    const [reportTarget, setReportTarget] = useState(null);
    const [reportCategory, setReportCategory] = useState('No se presentó');
    const [customCategory, setCustomCategory] = useState('');
    const [reportReason, setReportReason] = useState('');
    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelTarget, setCancelTarget] = useState(null);
    const [confirmTarget, setConfirmTarget] = useState(null);
    const [reschedTarget, setReschedTarget] = useState(null);
    const [reschedDate, setReschedDate] = useState('');
    const [reschedTime, setReschedTime] = useState('09:00');

    const fetchRequests = (page = 0) => {
        setLoading(true);
        const params = { page, size: 10 };
        const statusKey = FILTERS[filter].key;
        if (statusKey) {
            params.statuses = statusKey;
        }
        requestApi.getMyClientRequests(params)
            .then(data => {
                setRequests(data.content || []);
                setTotalPages(data.totalPages || 1);
                setCurrentPage(data.number || 0);
            })
            .catch(err => showToast('Error al cargar solicitudes: ' + err.message, 'error'))
            .finally(() => setLoading(false));
    };

    const handleCancel = () => {
        if (!cancelTarget) return;
        requestApi.cancelRequest(cancelTarget.requestId)
            .then(() => {
                setRequests(prev => prev.map(r =>
                    r.requestId === cancelTarget.requestId
                        ? { ...r, status: 'CANCELLED' }
                        : r
                ));
                setCancelOpen(false);
                setCancelTarget(null);
                showToast('Solicitud cancelada. Oferente notificado', 'success');
            })
            .catch(err => showToast('Error al cancelar: ' + err.message, 'danger'));
    };

    useEffect(() => {
        fetchRequests(0);
    }, [filter]);

    return (
        <DashboardLayout sections={CLIENT_NAV} avatar="JP">
            <div className="page-head">
                <div className="ph" style={{ margin: 0 }}><h1>Solicitudes activas</h1><p>Gestiona tus solicitudes en curso</p></div>
                <Link to="/services" className="btn btn-primary btn-sm"><Icon name="plus" size={13} />Nueva solicitud</Link>
            </div>

            <div className="filter-chips">
                {FILTERS.map((f, i) => (
                    <div key={f.label} className={`chip ${filter === i ? 'active' : ''}`} onClick={() => setFilter(i)}>{f.label}</div>
                ))}
            </div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando solicitudes...</div>
            ) : requests.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>
                    <p>No tienes solicitudes activas</p>
                    <Link to="/services" className="btn btn-primary btn-sm" style={{ marginTop: '12px' }}><Icon name="plus" size={13} />Solicitar un servicio</Link>
                </div>
            ) : (
                <>
                    {requests.map((r) => {
                        const st = STATUS_MAP[r.status] || { label: r.status, badge: '' };
                        const isNew = r.status === 'PENDING' && timeAgo(r.createdAt) === 'Hace 1 min';
                        const canConfirm = r.status === 'PRESUMABLY_COMPLETED';
                        const canReschedule = r.status === 'PENDING' || r.status === 'ACCEPTED';

                        return (
                            <div className={`req-card ${isNew ? 'new' : ''}`} key={r.requestId}>
                                <div className="rq-top">
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
                                        <span className={`badge ${st.badge}`}>{st.label}</span>
                                        <span style={{ fontSize: '11px', color: 'var(--c-soft)' }}>#{r.requestId}{timeAgo(r.createdAt) ? ` · ${timeAgo(r.createdAt)}` : ''}</span>
                                    </div>
                                    {!isTerminal(r.status) && <button className="btn btn-ghost btn-sm" onClick={() => { setReportTarget(r); setReportOpen(true); }} style={{ color: 'var(--c-danger)' }}><Icon name="alertTriangle" size={13} />Reportar</button>}
                                </div>
                                <div className="rq-main">
                                    <div className="av av-md">{getInitials(r.counterpartyName)}</div>
                                    <div style={{ flex: 1 }}>
                                        <div style={{ fontSize: '14px', fontWeight: 700 }}>{r.serviceTitle}</div>
                                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '2px' }}>{r.counterpartyName}{r.categoryName ? ` · ${r.categoryName}` : ''}</div>
                                        <div className="rq-meta">
                                            {r.scheduledDate && <span><Icon name="calendar" size={12} />{formatDate(r.scheduledDate)}</span>}
                                            {r.city && <span><Icon name="mapPin" size={12} />{r.city}</span>}
                                            {r.requestedPrice != null && <span><Icon name="dollar" size={12} />{formatPrice(r.requestedPrice)}</span>}
                                        </div>
                                    </div>
                                </div>
                                <div style={{ display: 'flex', gap: '7px', flexWrap: 'wrap' }}>
                                    <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate(`/requests/${r.requestId}`, { state: { as: 'client' } })}>Ver detalle</button>
                                    {canReschedule && <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => { setReschedTarget(r); setReschedOpen(true); }}><Icon name="reschedule" size={13} />Reprogramar</button>}
                                    {canConfirm && <button className="btn btn-primary btn-sm" onClick={() => { setConfirmTarget(r); setConfirmOpen(true); }}><Icon name="check" size={13} />Confirmar servicio</button>}
                                    {(r.status === 'PENDING' || r.status === 'ACCEPTED') && <button className="btn btn-danger btn-sm" onClick={() => { setCancelTarget(r); setCancelOpen(true); }}><Icon name="close" size={13} />Cancelar</button>}
                                </div>
                            </div>
                        );
                    })}
                    {totalPages > 1 && (
                        <div className="pager">
                            {Array.from({ length: totalPages }, (_, i) => (
                                <button
                                    key={i}
                                    className={`btn ${currentPage === i ? 'btn-primary' : 'btn-ghost'} btn-sm`}
                                    onClick={() => fetchRequests(i)}
                                >
                                    {i + 1}
                                </button>
                            ))}
                        </div>
                    )}
                </>
            )}

            <ReviewModal
                open={confirmOpen}
                onClose={() => { setConfirmOpen(false); setConfirmTarget(null); }}
                title="Confirmar servicio"
                sub={confirmTarget ? `¿El servicio "${confirmTarget.serviceTitle}" fue realizado correctamente por ${confirmTarget.counterpartyName}?` : '¿El servicio fue realizado correctamente?'}
                ratingLabel="Calificación"
                reviewLabel="Reseña"
                confirmLabel="Confirmar"
                onConfirm={async ({ rating, comment }) => {
                    if (!confirmTarget) return;
                    try {
                        await requestApi.confirmCompletion(confirmTarget.requestId);
                        if (rating > 0 || comment) {
                            await feedbackApi.submitServiceFeedback(confirmTarget.requestId, { rating: rating || null, comment: comment || null });
                        }
                        setRequests(prev => prev.map(r =>
                            r.requestId === confirmTarget.requestId ? { ...r, status: 'COMPLETED' } : r
                        ));
                        setConfirmOpen(false);
                        setConfirmTarget(null);
                        showToast('Servicio confirmado y reseña enviada', 'success');
                    } catch (err) {
                        showToast(err.message || 'No se pudo confirmar el servicio', 'danger');
                    }
                }}
            />

            <Modal open={reschedOpen} onClose={() => { setReschedOpen(false); setReschedTarget(null); }}>
                <div className="modal-title">Reprogramar solicitud</div>
                <div className="modal-sub">Elige nueva fecha y hora disponible</div>
                <div className="input-group"><label className="label">Nueva fecha</label><input className="input" type="date" value={reschedDate} onChange={(e) => setReschedDate(e.target.value)} min={new Date().toISOString().split('T')[0]} /></div>
                <div className="input-group"><label className="label">Nueva hora</label><select className="input" value={reschedTime} onChange={(e) => setReschedTime(e.target.value)}><option value="09:00">9:00 AM</option><option value="10:00">10:00 AM</option><option value="14:00">2:00 PM</option><option value="15:00">3:00 PM</option></select></div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => { setReschedOpen(false); setReschedTarget(null); }}>Cancelar</button>
                    <button className="btn btn-primary btn-full" disabled={!reschedDate} onClick={async () => {
                        if (!reschedTarget || !reschedDate) return;
                        try {
                            const newDate = `${reschedDate}T${reschedTime}:00`;
                            const result = await requestApi.rescheduleRequest(reschedTarget.requestId, { newDate });
                            setRequests(prev => prev.map(r =>
                                r.requestId === reschedTarget.requestId ? { ...r, status: 'RESCHEDULED' } : r
                            ));
                            if (result && result.requestId) {
                                setRequests(prev => [result, ...prev]);
                            }
                            setReschedOpen(false);
                            setReschedTarget(null);
                            setReschedDate('');
                            setReschedTime('09:00');
                            showToast('Solicitud reprogramada. Oferente notificado', 'success');
                        } catch (err) {
                            showToast(err.message || 'No se pudo reprogramar', 'danger');
                        }
                    }}>Confirmar</button>
                </div>
            </Modal>

            <Modal open={cancelOpen} onClose={() => { setCancelOpen(false); setCancelTarget(null); }}>
                <div className="modal-title">Cancelar solicitud</div>
                <div className="modal-sub">¿Estás seguro de cancelar esta solicitud? Esta acción no se puede deshacer.</div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => { setCancelOpen(false); setCancelTarget(null); }}>Volver</button>
                    <button className="btn btn-danger btn-full" onClick={handleCancel}><Icon name="close" size={13} />Sí, cancelar solicitud</button>
                </div>
            </Modal>

            <Modal open={reportOpen} onClose={() => { setReportOpen(false); setReportTarget(null); }}>
                <div className="modal-title">Reportar incumplimiento</div>
                <div className="modal-sub">
                    {reportTarget
                        ? <>Solicitud <strong>#{reportTarget.id}</strong>{reportTarget.serviceTitle ? ` · ${reportTarget.serviceTitle}` : ''}. El administrador revisará el caso.</>
                        : 'El administrador revisará el caso.'}
                </div>
                <div className="input-group"><label className="label">Categoría</label><select className="input" value={reportCategory} onChange={(e) => setReportCategory(e.target.value)}><option>No se presentó</option><option>Comportamiento inapropiado</option><option>Fraude</option><option>Otra</option></select></div>
                {reportCategory === 'Otra' && <div className="input-group"><label className="label">Categoría personalizada</label><input className="input" value={customCategory} onChange={(e) => setCustomCategory(e.target.value)} placeholder="Escribe la categoría" /></div>}
                <div className="input-group"><label className="label">Descripción</label><textarea className="input" rows="3" value={reportReason} onChange={(e) => setReportReason(e.target.value)} placeholder="Describe lo que ocurrió..." /></div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setReportOpen(false)}>Cancelar</button>
                    <button className="btn btn-danger btn-full" disabled={!reportReason.trim()} onClick={async () => {
                        try {
                            // RF-073: se reporta LA solicitud seleccionada. El backend deriva a quien se
                            // reporta desde la contraparte de esa solicitud, por eso no se manda reportedUserId
                            // (mandarlo permitiria incriminar a un tercero).
                            await reportApi.createRequestReport({
                                category: reportCategory,
                                customCategory,
                                reason: reportReason,
                                requestId: reportTarget?.requestId,
                            });
                            setReportOpen(false);
                            setReportCategory('No se presentó');
                            setCustomCategory('');
                            setReportReason('');
                            showToast('Reporte enviado', 'success');
                        } catch (error) {
                            showToast(error.message || 'No se pudo enviar el reporte', 'danger');
                        }
                    }}>Enviar reporte</button>
                </div>
            </Modal>
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default ClientRequestsPage;
