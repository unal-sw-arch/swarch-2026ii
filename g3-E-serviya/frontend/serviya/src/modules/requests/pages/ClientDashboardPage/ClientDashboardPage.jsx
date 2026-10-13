import { useState, useEffect, useCallback } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, StatCard, ToastContainer, useToast, CLIENT_NAV, requestApi, feedbackApi } from '../../../../shared';
import { ReviewModal } from '../../components/ReviewModal/ReviewModal';
import { metricsApi, notificationApi, profileApi, proposalApi } from '../../../../shared/api';
import { STATUS_MAP, formatDate, timeAgo, categoryIcon } from '../../utils';

import './ClientDashboardPage.css';

const TYPE_META = {
    new_request:         { icon: 'tasks',     cls: '' },
    request_accepted:    { icon: 'check',     cls: 'success' },
    request_rejected:    { icon: 'close',     cls: 'danger' },
    request_cancelled:   { icon: 'close',     cls: 'danger' },
    service_completed:   { icon: 'check',     cls: 'success' },
    reschedule_proposed: { icon: 'reschedule', cls: 'warn' },
    request_rescheduled: { icon: 'reschedule', cls: 'warn' },
};

function metaForType(type) {
    return TYPE_META[type] || { icon: 'bell', cls: '' };
}

function initials(name) {
    if (!name) return '??';
    return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

export function ClientDashboardPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [selectedRequest, setSelectedRequest] = useState(null);
    const [reschedOpen, setReschedOpen] = useState(false);
    const [cancelOpen, setCancelOpen] = useState(false);
    const [cancelTarget, setCancelTarget] = useState(null);
    const [proposals, setProposals] = useState([]);
    const [loadingProposals, setLoadingProposals] = useState(true);
    const [reschedTarget, setReschedTarget] = useState(null);
    const [reschedDate, setReschedDate] = useState('');
    const [reschedTime, setReschedTime] = useState('09:00');
    const [clientMetrics, setClientMetrics] = useState(null);
    const [loading, setLoading] = useState(true);
    const [requests, setRequests] = useState([]);
    const [loadingRequests, setLoadingRequests] = useState(true);
    const [notifications, setNotifications] = useState([]);
    const [loadingNotifications, setLoadingNotifications] = useState(true);
    const [profile, setProfile] = useState(null);

    useEffect(() => {
        metricsApi.getMyMetrics()
            .then(data => setClientMetrics(data.clientMetrics))
            .catch(() => showToast('Error al cargar métricas', 'danger'))
            .finally(() => setLoading(false));
    }, [showToast]);

    const loadRequests = useCallback(() => {
        setLoadingRequests(true);
        requestApi.getMyClientRequests({ page: 0, size: 5 })
            .then(data => setRequests(data.content || []))
            .catch(() => showToast('Error al cargar solicitudes', 'danger'))
            .finally(() => setLoadingRequests(false));
    }, [showToast]);

    useEffect(() => { loadRequests(); }, [loadRequests]);

    useEffect(() => {
        notificationApi.getNotifications({ page: 0, size: 3 })
            .then(data => setNotifications(data.content || []))
            .catch(() => {})
            .finally(() => setLoadingNotifications(false));
    }, []);

    useEffect(() => {
        profileApi.getMyProfile()
            .then(data => setProfile(data))
            .catch(() => {});
    }, []);

    const userName = profile?.fullName || profile?.name || '';
    const avatarText = initials(userName);

    useEffect(() => {
        proposalApi.getReceived({ page: 0, size: 5, statuses: 'PENDING' })
            .then(data => setProposals(data.content || []))
            .catch(() => {})
            .finally(() => setLoadingProposals(false));
    }, []);

    const handleCancel = () => {
        if (!cancelTarget) return;
        requestApi.cancelRequest(cancelTarget.requestId)
            .then(() => {
                loadRequests();
                setCancelOpen(false);
                setCancelTarget(null);
                showToast('Solicitud cancelada. Oferente notificado', 'success');
            })
            .catch(err => showToast('Error al cancelar: ' + err.message, 'danger'));
    };

    async function handleConfirmCompletion({ rating, comment } = {}) {
        if (!selectedRequest) return;
        try {
            await requestApi.confirmCompletion(selectedRequest.requestId);
            if (rating > 0 || comment) {
                await feedbackApi.submitServiceFeedback(selectedRequest.requestId, { rating: rating || null, comment: comment || null });
            }
            setRequests(prev => prev.map(r =>
                r.requestId === selectedRequest.requestId ? { ...r, status: 'COMPLETED' } : r
            ));
            setConfirmOpen(false);
            setSelectedRequest(null);
            showToast('Servicio confirmado y reseña enviada', 'success');
            loadRequests();
        } catch (e) {
            showToast(e.message || 'Error al confirmar servicio', 'danger');
        }
    }

    function openConfirmModal(req) {
        setSelectedRequest(req);
        setConfirmOpen(true);
    }

    const handleAcceptProposal = async (proposal) => {
        try {
            const newRequest = await proposalApi.acceptProposal(proposal.proposalId);
            setProposals(prev => prev.filter(p => p.proposalId !== proposal.proposalId));
            if (newRequest && newRequest.requestId) {
                setRequests(prev => [newRequest, ...prev]);
            }
            showToast('Propuesta aceptada. Servicio reprogramado', 'success');
        } catch (err) {
            showToast(err.message || 'No se pudo aceptar la propuesta', 'danger');
        }
    };

    const handleRejectProposal = async (proposal) => {
        try {
            await proposalApi.rejectProposal(proposal.proposalId);
            setProposals(prev => prev.filter(p => p.proposalId !== proposal.proposalId));
            showToast('Propuesta rechazada', 'success');
        } catch (err) {
            showToast(err.message || 'No se pudo rechazar la propuesta', 'danger');
        }
    };

    const stats = clientMetrics ? [
        { icon: 'tasks', value: String(clientMetrics.activeRequests ?? 0), label: 'Solicitudes activas' },
        { icon: 'checkCircle', value: String(clientMetrics.totalCompletedRequests ?? 0), label: 'Servicios completados', variant: 'success' },
        { icon: 'star', value: (clientMetrics.averageRating ?? 0).toFixed(1) + '★', label: 'Mi calificación', variant: 'warn', fill: 'currentColor' },
        {
            icon: 'xCircle',
            value: (clientMetrics.totalRequestsSent > 0 ? (clientMetrics.totalCancelledRequests / clientMetrics.totalRequestsSent * 100) : 0).toFixed(0) + '%',
            label: 'Tasa cancelaciones',
            variant: 'danger',
        },
    ] : [];

    return (
        <DashboardLayout sections={CLIENT_NAV} avatar={avatarText}>
            <div className="ph">
                <h1>{userName ? `¡Hola, ${userName}!` : '¡Hola!'}</h1>
                <p>Aquí tienes un resumen de tu actividad en ServiYa</p>
            </div>

            <div className="g4" style={{ marginBottom: '22px' }}>
                {loading ? <div className="loading-pulse" style={{ height: 80 }} /> : stats.map((s) => <StatCard key={s.label} {...s} />)}
            </div>

            {!loadingProposals && proposals.length > 0 && (
                <div className="card resched-banner">
                    <div className="resched-head">
                        <Icon name="reschedule" size={18} style={{ color: 'var(--c-warn)' }} />
                        <span style={{ fontSize: '14px', fontWeight: 700 }}>Propuesta de reprogramación</span>
                        <span className="badge badge-warn">Requiere acción</span>
                    </div>
                    <div className="resched-body">
                        <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '4px' }}>{proposals[0].serviceTitle} — {proposals[0].counterpartyName}</div>
                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '10px' }}>
                            Propone cambiar de <strong>{formatDate(proposals[0].originalScheduledDate)}</strong> a <strong>{formatDate(proposals[0].proposedDate)}</strong><br />
                            {proposals[0].reason && <span style={{ fontStyle: 'italic', marginTop: '3px', display: 'block' }}>Motivo: "{proposals[0].reason}"</span>}
                        </div>
                        <div style={{ display: 'flex', gap: '7px', flexWrap: 'wrap' }}>
                            <button className="btn btn-success btn-sm" onClick={() => handleAcceptProposal(proposals[0])}><Icon name="check" size={13} />Aceptar propuesta</button>
                            <button className="btn btn-danger btn-sm" onClick={() => handleRejectProposal(proposals[0])}><Icon name="close" size={13} />Rechazar</button>
                        </div>
                    </div>
                </div>
            )}

            <div className="g2" style={{ gap: '20px' }}>
                <div>
                    <div className="col-head">
                        <div style={{ fontSize: '15px', fontWeight: 700 }}>Solicitudes activas</div>
                        <Link to="/requests" className="link-more">Ver todas →</Link>
                    </div>
                    {loadingRequests ? (
                        <div style={{ padding: '20px 0', color: 'var(--c-soft)', fontSize: '13px' }}>Cargando solicitudes...</div>
                    ) : requests.length === 0 ? (
                        <div style={{ padding: '20px 0', color: 'var(--c-soft)', fontSize: '13px' }}>No tienes solicitudes activas</div>
                    ) : (
                        requests.map((r) => {
                            const st = STATUS_MAP[r.status] || { label: r.status, badge: '' };
                            const isNew = r.status === 'PENDING' && timeAgo(r.createdAt) === 'Hace 1 min';
                            const canConfirm = r.status === 'PRESUMABLY_COMPLETED';
                            const canReschedule = r.status === 'PENDING' || r.status === 'ACCEPTED';
                            const meta = [r.counterpartyName, formatDate(r.scheduledDate), r.city].filter(Boolean).join(' · ');

                            return (
                                <div className={`req-card ${isNew ? 'new' : ''}`} key={r.requestId}>
                                    <div className="req-header">
                                        <div className="req-icon"><Icon name={categoryIcon(r.categoryName)} size={20} /></div>
                                        <div className="req-info"><div className="req-name">{r.serviceTitle}</div><div className="req-meta">{meta}</div></div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                                            <span className={`sdot sdot-${r.status === 'PENDING' ? 'pending' : 'accepted'}`} />
                                            <span className={`badge ${st.badge}`}>{st.label}</span>
                                        </div>
                                    </div>
                                    <div className="req-actions">
                                        <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate(`/requests/${r.requestId}`, { state: { as: 'client' } })}>Ver detalle</button>
                                        {canReschedule && <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => { setReschedTarget(r); setReschedOpen(true); }}><Icon name="reschedule" size={13} />Reprogramar</button>}
                                        {canConfirm && <button className="btn btn-primary btn-sm" onClick={() => openConfirmModal(r)}><Icon name="check" size={13} />Confirmar servicio</button>}
                                        {(r.status === 'PENDING' || r.status === 'ACCEPTED') && <button className="btn btn-danger btn-sm" onClick={() => { setCancelTarget(r); setCancelOpen(true); }}><Icon name="close" size={13} />Cancelar</button>}
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>

                <div>
                    <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Notificaciones recientes</div>
                    {loadingNotifications ? (
                        <div style={{ padding: '12px 0', color: 'var(--c-soft)', fontSize: '13px' }}>Cargando notificaciones...</div>
                    ) : notifications.length === 0 ? (
                        <div style={{ padding: '12px 0', color: 'var(--c-soft)', fontSize: '13px' }}>No hay notificaciones</div>
                    ) : notifications.map((n) => {
                        const meta = metaForType(n.notificationType);
                        const isUnread = !n.readAt;
                        return (
                            <div className={`notif-item ${isUnread ? 'unread' : 'read'}`} key={n.deliveryId}>
                                <div className={`notif-ico ${meta.cls}`}><Icon name={meta.icon} size={16} /></div>
                                <div className="notif-body">
                                    <div className="notif-title">{n.title}</div>
                                    <div className="notif-msg">{n.message}</div>
                                    <div className="notif-time">{timeAgo(n.createdAt)}</div>
                                </div>
                            </div>
                        );
                    })}
                    <Link to="/notifications" className="link-more" style={{ display: 'block', textAlign: 'center', marginTop: '10px' }}>Ver todas las notificaciones →</Link>
                </div>
            </div>

            <ReviewModal
                open={confirmOpen}
                onClose={() => { setConfirmOpen(false); setSelectedRequest(null); }}
                title="Confirmar cumplimiento del servicio"
                sub={selectedRequest ? `¿El servicio "${selectedRequest.serviceTitle}" fue realizado correctamente por ${selectedRequest.counterpartyName}?` : ''}
                ratingLabel="Calificación del servicio (RF-041)"
                reviewLabel="Reseña del servicio (RF-045)"
                confirmLabel="Confirmar servicio"
                onConfirm={handleConfirmCompletion}
            />

            <Modal open={reschedOpen} onClose={() => { setReschedOpen(false); setReschedTarget(null); }}>
                <div className="modal-title">Reprogramar solicitud</div>
                <div className="modal-sub">Elige una nueva fecha y hora disponible del oferente</div>
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
                    }}>Confirmar reprogramación</button>
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
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default ClientDashboardPage;
