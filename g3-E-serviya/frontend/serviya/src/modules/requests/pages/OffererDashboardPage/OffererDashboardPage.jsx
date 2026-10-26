import { useState, useEffect, useCallback } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, StatCard, ToastContainer, useToast, OFFERER_NAV, requestApi, feedbackApi } from '../../../../shared';
import { ReviewModal } from '../../components/ReviewModal/ReviewModal';
import { metricsApi, notificationApi, profileApi, proposalApi } from '../../../../shared/api';
import { timeAgo } from '../../utils';

import './OffererDashboardPage.css';

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

const STATUS_CONFIG = {
    PENDING:                { badgeClass: 'badge-primary', label: 'Nueva' },
    ACCEPTED:               { badgeClass: 'badge-success', label: 'Aceptada' },
    PRESUMABLY_COMPLETED:   { badgeClass: 'badge-warn',    label: 'Esperando confirmación' },
};

function formatScheduledDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true });
}

function timeAgoShort(dateStr) {
    if (!dateStr) return '';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Recibida hace un momento';
    if (mins < 60) return `Recibida hace ${mins} min`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `Recibida hace ${hours}h`;
    const days = Math.floor(hours / 24);
    return `Recibida hace ${days}d`;
}

function initials(name) {
    if (!name) return '??';
    return name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

export function OffererDashboardPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [proposalOpen, setProposalOpen] = useState(false);
    const [completeOpen, setCompleteOpen] = useState(false);
    const [selectedRequest, setSelectedRequest] = useState(null);
    const [proposalTarget, setProposalTarget] = useState(null);
    const [proposalDate, setProposalDate] = useState('');
    const [proposalTime, setProposalTime] = useState('09:00');
    const [proposalReason, setProposalReason] = useState('');
    const [proposalSubmitting, setProposalSubmitting] = useState(false);
    const [offererMetrics, setOffererMetrics] = useState(null);
    const [loading, setLoading] = useState(true);
    const [requests, setRequests] = useState([]);
    const [loadingRequests, setLoadingRequests] = useState(true);
    const [notifications, setNotifications] = useState([]);
    const [loadingNotifications, setLoadingNotifications] = useState(true);
    const [profile, setProfile] = useState(null);
    const [acting, setActing] = useState(null);

    const loadRequests = useCallback(() => {
        setLoadingRequests(true);
        requestApi.getOffererRequests({ statuses: ['PENDING', 'ACCEPTED', 'PRESUMABLY_COMPLETED'], size: 20 })
            .then(data => setRequests(data.content || []))
            .catch(() => showToast('Error al cargar solicitudes', 'danger'))
            .finally(() => setLoadingRequests(false));
    }, [showToast]);

    useEffect(() => {
        metricsApi.getMyMetrics()
            .then(data => setOffererMetrics(data.offererMetrics))
            .catch(() => showToast('Error al cargar métricas', 'danger'))
            .finally(() => setLoading(false));
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

    const stats = offererMetrics ? [
        { icon: 'tasks', value: String(offererMetrics.totalRequestsReceived ?? 0), label: 'Solicitudes recibidas' },
        { icon: 'checkCircle', value: String(offererMetrics.totalCompletedServices ?? 0), label: 'Servicios completados', variant: 'success' },
        { icon: 'star', value: (offererMetrics.averageRating ?? 0).toFixed(1) + '★', label: 'Calificación promedio', variant: 'warn', fill: 'currentColor' },
        {
            icon: 'xCircle',
            value: (offererMetrics.totalRequestsReceived > 0 ? (offererMetrics.totalCancelledServices / offererMetrics.totalRequestsReceived * 100) : 0).toFixed(0) + '%',
            label: 'Cancelaciones',
            variant: 'danger',
        },
    ] : [];

    async function handleAccept(req) {
        setActing(req.requestId);
        try {
            await requestApi.acceptRequest(req.requestId);
            showToast('Solicitud aceptada. Cliente notificado', 'success');
            loadRequests();
        } catch (e) {
            showToast(e.message || 'Error al aceptar solicitud', 'danger');
        } finally {
            setActing(null);
        }
    }

    async function handleReject(req) {
        setActing(req.requestId);
        try {
            await requestApi.rejectRequest(req.requestId);
            showToast('Solicitud rechazada. Cliente notificado', 'success');
            loadRequests();
        } catch (e) {
            showToast(e.message || 'Error al rechazar solicitud', 'danger');
        } finally {
            setActing(null);
        }
    }

    async function handleMarkCompleted({ rating, comment } = {}) {
        if (!selectedRequest) return;
        setActing(selectedRequest.requestId);
        try {
            await requestApi.markCompleted(selectedRequest.requestId);
            if (rating > 0 || comment) {
                await feedbackApi.submitClientFeedback(selectedRequest.requestId, {
                    clientId: selectedRequest.counterpartyId,
                    rating: rating || null,
                    comment: comment || null,
                });
            }
            setCompleteOpen(false);
            setSelectedRequest(null);
            showToast('Servicio marcado como realizado. Reseña enviada', 'success');
            loadRequests();
        } catch (e) {
            showToast(e.message || 'Error al marcar servicio', 'danger');
        } finally {
            setActing(null);
        }
    }

    function openCompleteModal(req) {
        setSelectedRequest(req);
        setCompleteOpen(true);
    }

    return (
        <DashboardLayout sections={OFFERER_NAV} avatar={avatarText}>
            <div className="ph">
                <h1>{userName ? `¡Hola, ${userName}!` : '¡Hola!'}</h1>
                <p>Gestiona tus servicios y solicitudes recibidas</p>
            </div>

            <div className="g4" style={{ marginBottom: '22px' }}>
                {loading ? <div className="loading-pulse" style={{ height: 80 }} /> : stats.map((s) => <StatCard key={s.label} {...s} />)}
            </div>

            <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '14px' }}>Solicitudes recibidas</div>

            {loadingRequests ? (
                <div className="loading-pulse" style={{ height: 120, marginBottom: 10 }} />
            ) : requests.length === 0 ? (
                <div className="card" style={{ padding: '20px', textAlign: 'center', color: 'var(--c-mid)', fontSize: '13px' }}>
                    No tienes solicitudes activas en este momento.
                </div>
            ) : requests.map((req) => {
                const st = STATUS_CONFIG[req.status] || { badgeClass: 'badge-gray', label: req.status };
                const isPending = req.status === 'PENDING';
                const isAccepted = req.status === 'ACCEPTED';

                return (
                    <div className={`req-card${isPending ? ' new' : ''}`} style={{ marginBottom: '10px' }} key={req.requestId}>
                        <div className="inc-head">
                            <span className={`badge ${st.badgeClass}`}>{st.label}</span>
                            <span style={{ fontSize: '11px', color: 'var(--c-soft)' }}>{timeAgoShort(req.createdAt)}</span>
                        </div>
                        <div className="inc-body">
                            <div className="av av-md">{initials(req.counterpartyName)}</div>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--c-text)' }}>{req.counterpartyName}</div>
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>
                                    {req.serviceTitle} · {req.categoryName} · {formatScheduledDate(req.scheduledDate)}
                                    {req.city ? ` · ${req.city}` : ''}
                                </div>
                                {req.requestedPrice && (
                                    <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '2px' }}>
                                        Precio: ${req.requestedPrice}
                                    </div>
                                )}
                            </div>
                        </div>
                        <div style={{ display: 'flex', gap: '7px', flexWrap: 'wrap' }}>
                            <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate(`/requests/${req.requestId}`, { state: { as: 'offerer' } })}>
                                Ver detalle
                            </button>
                            {isPending && (
                                <>
                                    <button className="btn btn-success btn-sm" disabled={acting === req.requestId} onClick={() => handleAccept(req)}>
                                        <Icon name="check" size={13} />Aceptar
                                    </button>
                                    <button className="btn btn-danger btn-sm" disabled={acting === req.requestId} onClick={() => handleReject(req)}>
                                        <Icon name="close" size={13} />Rechazar
                                    </button>
                                </>
                            )}
                            {isAccepted && (
                                <>
                                    <button className="btn btn-primary btn-sm" disabled={acting === req.requestId} onClick={() => openCompleteModal(req)}>
                                        <Icon name="check" size={13} />Marcar como realizado
                                    </button>
                                    <button className="btn btn-danger btn-sm" disabled={acting === req.requestId} onClick={() => handleReject(req)}>
                                        Cancelar
                                    </button>
                                    <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => { setProposalTarget(req); setProposalOpen(true); }}>
                                        Reprogramar
                                    </button>
                                </>
                            )}
                            {req.status === 'PRESUMABLY_COMPLETED' && (
                                <span style={{ fontSize: '12px', color: 'var(--c-mid)', fontStyle: 'italic' }}>
                                    Esperando confirmación del cliente...
                                </span>
                            )}
                        </div>
                    </div>
                );
            })}

            <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px', marginTop: '20px' }}>Notificaciones recientes</div>
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

            <Modal open={proposalOpen} onClose={() => { setProposalOpen(false); setProposalTarget(null); }}>
                <div className="modal-title">Proponer reprogramación</div>
                <div className="modal-sub">El cliente recibirá tu propuesta y podrá aceptarla o rechazarla.</div>
                <div className="input-group"><label className="label">Nueva fecha propuesta</label><input className="input" type="date" value={proposalDate} onChange={(e) => setProposalDate(e.target.value)} min={new Date().toISOString().split('T')[0]} /></div>
                <div className="input-group"><label className="label">Nueva hora propuesta</label><select className="input" value={proposalTime} onChange={(e) => setProposalTime(e.target.value)}><option value="09:00">9:00 AM</option><option value="10:00">10:00 AM</option><option value="11:00">11:00 AM</option><option value="14:00">2:00 PM</option><option value="15:00">3:00 PM</option></select></div>
                <div className="input-group"><label className="label">Motivo de la reprogramación</label><textarea className="input" rows="3" placeholder="Explica brevemente el motivo..." value={proposalReason} onChange={(e) => setProposalReason(e.target.value)} /></div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => { setProposalOpen(false); setProposalTarget(null); }}>Cancelar</button>
                    <button className="btn btn-primary btn-full" disabled={!proposalDate || proposalSubmitting} onClick={async () => {
                        if (!proposalTarget || !proposalDate || proposalSubmitting) return;
                        // Guard contra doble/triple envío: sin esto, clics rápidos o un freeze disparan
                        // varios createProposal concurrentes y el backend crea varias PENDING (condición
                        // de carrera; ver estado-frontend-pendientes.md / NOTAS.txt).
                        setProposalSubmitting(true);
                        try {
                            const proposedDate = `${proposalDate}T${proposalTime}:00`;
                            await proposalApi.createProposal({
                                requestId: proposalTarget.requestId,
                                reason: proposalReason || null,
                                proposedDate,
                            });
                            setProposalOpen(false);
                            setProposalTarget(null);
                            setProposalDate('');
                            setProposalTime('09:00');
                            setProposalReason('');
                            showToast('Propuesta enviada al cliente', 'success');
                        } catch (err) {
                            showToast(err.message || 'No se pudo enviar la propuesta', 'danger');
                        } finally {
                            setProposalSubmitting(false);
                        }
                    }}><Icon name="send" size={15} />{proposalSubmitting ? 'Enviando…' : 'Enviar propuesta'}</button>
                </div>
            </Modal>

            <ReviewModal
                open={completeOpen}
                onClose={() => { setCompleteOpen(false); setSelectedRequest(null); }}
                title="Confirmar servicio realizado"
                sub={selectedRequest ? `Confirma que prestaste el servicio a ${selectedRequest.counterpartyName} correctamente.` : ''}
                ratingLabel="Califica al cliente (RF-043)"
                reviewLabel="Reseña del cliente (RF-044)"
                confirmLabel="Confirmar realizado"
                confirmClass="btn-success"
                onConfirm={handleMarkCompleted}
            />
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default OffererDashboardPage;
