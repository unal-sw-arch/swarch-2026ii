import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom';
import {
    DashboardLayout, Avatar, Icon, Modal, ToastContainer, useToast,
    CLIENT_NAV, OFFERER_NAV, requestApi, proposalApi, feedbackApi, getApiImageUrl,
} from '../../../../shared';
import { ReviewModal } from '../../components/ReviewModal/ReviewModal';
import { STATUS_MAP, formatDate, formatPrice, getInitials, categoryIcon } from '../../utils';

import './RequestDetailPage.css';

const TIME_OPTIONS = [
    ['09:00', '9:00 AM'], ['10:00', '10:00 AM'], ['11:00', '11:00 AM'],
    ['14:00', '2:00 PM'], ['15:00', '3:00 PM'], ['16:00', '4:00 PM'],
];

export function RequestDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const { toasts, showToast } = useToast();

    // El backend resuelve "counterparty" como la OTRA parte. El rol desde el que se navegó
    // (state.as) decide el rótulo y qué acciones ofrecer; por defecto cliente.
    const viewerAs = location.state?.as === 'offerer' ? 'offerer' : 'client';
    const isOfferer = viewerAs === 'offerer';
    const counterpartyLabel = isOfferer ? 'Cliente' : 'Oferente';
    const sections = isOfferer ? OFFERER_NAV : CLIENT_NAV;

    const [detail, setDetail] = useState(null);
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [acting, setActing] = useState(false);

    // Existencia de feedback (null = aún no comprobado). Decide mostrar "Calificar ...".
    const [hasServiceFeedback, setHasServiceFeedback] = useState(null);
    const [hasClientFeedback, setHasClientFeedback] = useState(null);

    // Modal de reseña: 'confirm' (cliente confirma+califica servicio) | 'rateService' (solo califica
    // servicio) | 'complete' (oferente marca completado+califica cliente) | 'rateClient' (solo califica cliente)
    const [reviewAction, setReviewAction] = useState(null);
    const [reschedOpen, setReschedOpen] = useState(false);   // cliente: reprogramación directa
    const [proposalOpen, setProposalOpen] = useState(false); // oferente: propuesta de reprogramación
    const [dateVal, setDateVal] = useState('');
    const [timeVal, setTimeVal] = useState('09:00');
    const [reasonVal, setReasonVal] = useState('');

    const loadDetail = useCallback(() => {
        requestApi.getRequestDetail(id)
            .then((d) => {
                setDetail(d);
                // ¿Ya hay calificación? Endpoint booleano dedicado. Solo se consulta la que aplica.
                if (!isOfferer && d.status === 'COMPLETED') {
                    feedbackApi.serviceFeedbackExists(id).then(setHasServiceFeedback).catch(() => setHasServiceFeedback(false));
                }
                if (isOfferer && (d.status === 'PRESUMABLY_COMPLETED' || d.status === 'COMPLETED')) {
                    feedbackApi.clientFeedbackExists(id).then(setHasClientFeedback).catch(() => setHasClientFeedback(false));
                }
            })
            .catch((err) => setError(err.message || 'No se pudo cargar la solicitud'))
            .finally(() => setLoading(false));
        requestApi.getRequestHistory(id)
            .then((data) => setHistory(Array.isArray(data) ? data : []))
            .catch(() => { /* el historial es opcional */ });
    }, [id, isOfferer]);

    useEffect(() => { loadDetail(); }, [loadDetail]);

    // --- Acciones simples (con refresco) ---
    const runAction = async (fn, okMsg) => {
        setActing(true);
        try {
            await fn();
            showToast(okMsg, 'success');
            loadDetail();
        } catch (e) {
            showToast(e.message || 'No se pudo completar la acción', 'danger');
        } finally {
            setActing(false);
        }
    };

    const handleAccept = () => runAction(() => requestApi.acceptRequest(id), 'Solicitud aceptada. Cliente notificado');
    const handleReject = () => {
        if (!window.confirm('¿Rechazar esta solicitud? El cliente será notificado.')) return;
        runAction(() => requestApi.rejectRequest(id), 'Solicitud rechazada');
    };
    const handleCancel = () => {
        if (!window.confirm('¿Cancelar esta solicitud? Esta acción no se puede deshacer.')) return;
        runAction(() => requestApi.cancelRequest(id), 'Solicitud cancelada. Contraparte notificada');
    };

    // Cliente: reprogramación directa (crea una nueva solicitud PENDING).
    const handleReschedule = async () => {
        if (!dateVal) return;
        setActing(true);
        try {
            const result = await requestApi.rescheduleRequest(id, { newDate: `${dateVal}T${timeVal}:00` });
            closeDialogs();
            showToast('Solicitud reprogramada. Contraparte notificada', 'success');
            if (result && (result.requestId || result.id)) {
                navigate(`/requests/${result.requestId || result.id}`, { state: { as: viewerAs } });
            } else {
                loadDetail();
            }
        } catch (e) {
            showToast(e.message || 'No se pudo reprogramar', 'danger');
        } finally {
            setActing(false);
        }
    };

    // Oferente: propuesta de reprogramación (el cliente la acepta/rechaza después).
    const handleProposal = async () => {
        if (!dateVal) return;
        setActing(true);
        try {
            await proposalApi.createProposal({
                requestId: Number(id),
                reason: reasonVal || null,
                proposedDate: `${dateVal}T${timeVal}:00`,
            });
            closeDialogs();
            showToast('Propuesta de reprogramación enviada al cliente', 'success');
            loadDetail();
        } catch (e) {
            showToast(e.message || 'No se pudo enviar la propuesta', 'danger');
        } finally {
            setActing(false);
        }
    };

    // Reseña: confirmar/calificar (cliente → servicio) o completar/calificar (oferente → cliente).
    const handleReviewConfirm = async ({ rating, comment } = {}) => {
        setActing(true);
        const hasReview = rating > 0 || !!comment;
        const payload = { rating: rating || null, comment: comment || null };
        try {
            if (reviewAction === 'confirm') {
                await requestApi.confirmCompletion(id);
                if (hasReview) await feedbackApi.submitServiceFeedback(id, payload);
                showToast('Servicio confirmado', 'success');
            } else if (reviewAction === 'rateService') {
                if (hasReview) await feedbackApi.submitServiceFeedback(id, payload);
                showToast('Reseña del servicio enviada', 'success');
            } else if (reviewAction === 'complete') {
                await requestApi.markCompleted(id);
                // El feedback de cliente exige clientId; para el oferente la contraparte ES el cliente.
                if (hasReview) await feedbackApi.submitClientFeedback(id, { ...payload, clientId: detail.counterpartyId });
                showToast('Servicio marcado como completado', 'success');
            } else if (reviewAction === 'rateClient') {
                if (hasReview) await feedbackApi.submitClientFeedback(id, { ...payload, clientId: detail.counterpartyId });
                showToast('Cliente calificado', 'success');
            }
            setReviewAction(null);
            loadDetail();
        } catch (e) {
            showToast(e.message || 'No se pudo completar la acción', 'danger');
        } finally {
            setActing(false);
        }
    };

    const closeDialogs = () => {
        setReschedOpen(false);
        setProposalOpen(false);
        setDateVal('');
        setTimeVal('09:00');
        setReasonVal('');
    };

    const renderShell = (children) => (
        <DashboardLayout sections={sections}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '18px' }}>
                <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate(-1)}>
                    <Icon name="chevronLeft" size={14} />Volver
                </button>
            </div>
            {children}
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );

    if (loading) {
        return renderShell(
            <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando solicitud...</div>
        );
    }

    if (error || !detail) {
        return renderShell(
            <div className="card" style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--c-mid)' }}>
                <Icon name="alertTriangle" size={40} style={{ color: 'var(--c-danger)', marginBottom: '10px' }} />
                <p>{error || 'La solicitud no existe o no tienes acceso a ella.'}</p>
            </div>
        );
    }

    const st = STATUS_MAP[detail.status] || { label: detail.status, badge: '' };
    const currentId = Number(id);
    const status = detail.status;

    // Botones de acción según rol + estado.
    const actionButtons = [];
    if (isOfferer) {
        if (status === 'PENDING') {
            actionButtons.push(
                <button key="acc" className="btn btn-success btn-full" disabled={acting} onClick={handleAccept}><Icon name="check" size={15} />Aceptar</button>,
                <button key="rej" className="btn btn-danger btn-full" disabled={acting} onClick={handleReject}><Icon name="close" size={15} />Rechazar</button>,
            );
        } else if (status === 'ACCEPTED') {
            actionButtons.push(
                <button key="comp" className="btn btn-primary btn-full" disabled={acting} onClick={() => setReviewAction('complete')}><Icon name="check" size={15} />Marcar completado</button>,
                <button key="prop" className="btn btn-outline btn-full" disabled={acting} onClick={() => setProposalOpen(true)}><Icon name="reschedule" size={15} />Proponer reprogramación</button>,
                <button key="can" className="btn btn-danger btn-full" disabled={acting} onClick={handleCancel}><Icon name="close" size={15} />Cancelar</button>,
            );
        }
        if ((status === 'PRESUMABLY_COMPLETED' || status === 'COMPLETED') && hasClientFeedback === false) {
            actionButtons.push(
                <button key="ratec" className="btn btn-primary btn-full" disabled={acting} onClick={() => setReviewAction('rateClient')}><Icon name="star" size={15} />Calificar cliente</button>,
            );
        }
    } else {
        if (status === 'PENDING' || status === 'ACCEPTED') {
            actionButtons.push(
                <button key="res" className="btn btn-outline btn-full" disabled={acting} onClick={() => setReschedOpen(true)}><Icon name="reschedule" size={15} />Reprogramar</button>,
                <button key="can" className="btn btn-danger btn-full" disabled={acting} onClick={handleCancel}><Icon name="close" size={15} />Cancelar</button>,
            );
        } else if (status === 'PRESUMABLY_COMPLETED') {
            actionButtons.push(
                <button key="con" className="btn btn-primary btn-full" disabled={acting} onClick={() => setReviewAction('confirm')}><Icon name="check" size={15} />Confirmar servicio</button>,
            );
        }
        if (status === 'COMPLETED' && hasServiceFeedback === false) {
            actionButtons.push(
                <button key="rates" className="btn btn-primary btn-full" disabled={acting} onClick={() => setReviewAction('rateService')}><Icon name="star" size={15} />Calificar servicio</button>,
            );
        }
    }

    const related = [...history].sort(
        (a, b) => new Date(a.updatedStatusAt || 0) - new Date(b.updatedStatusAt || 0)
    );

    const ratesClient = reviewAction === 'complete' || reviewAction === 'rateClient';
    const reviewTitle = {
        confirm: 'Confirmar cumplimiento del servicio',
        rateService: 'Calificar servicio',
        complete: 'Marcar servicio como completado',
        rateClient: 'Calificar cliente',
    }[reviewAction];

    return renderShell(
        <>
            <div className="rd-header card">
                <div className="rd-header-icon"><Icon name={categoryIcon(detail.categoryName)} size={24} /></div>
                <div style={{ flex: 1 }}>
                    <div style={{ fontSize: '18px', fontWeight: 800 }}>{detail.serviceTitle}</div>
                    <div style={{ fontSize: '13px', color: 'var(--c-mid)' }}>{detail.categoryName}</div>
                </div>
                <span className={`badge ${st.badge}`}>{st.label}</span>
            </div>

            {detail.previousRequestId && (
                <div className="note-box" style={{ marginBottom: '16px' }}>
                    <Icon name="reschedule" size={14} /> Esta solicitud se creó al reprogramar la{' '}
                    <Link to={`/requests/${detail.previousRequestId}`} state={{ as: viewerAs }}>solicitud anterior</Link>.
                </div>
            )}

            <div className="g2" style={{ gap: '20px', alignItems: 'start' }}>
                {/* Columna principal */}
                <div>
                    <div className="card rd-card">
                        <div className="rd-card-title">Detalles de la solicitud</div>
                        <div className="rd-fields">
                            <Field label="Estado" value={st.label} />
                            <Field label="Fecha programada" value={formatDate(detail.scheduledDate)} />
                            <Field label="Precio acordado" value={detail.requestedPrice != null ? formatPrice(detail.requestedPrice) : '—'} />
                            <Field label="Creada" value={formatDate(detail.createdAt)} />
                            {detail.completedAt && <Field label="Completada" value={formatDate(detail.completedAt)} />}
                            <Field label="Última modificación" value={formatDate(detail.updatedStatusAt)} />
                        </div>
                    </div>

                    <div className="card rd-card">
                        <div className="rd-card-title">Dirección del servicio</div>
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                            <Icon name="mapPin" size={18} style={{ color: 'var(--c-primary)', marginTop: '2px' }} />
                            <div>
                                <div style={{ fontWeight: 600, fontSize: '14px' }}>{detail.addressLine || 'Sin dirección'}</div>
                                {detail.city && <div style={{ fontSize: '13px', color: 'var(--c-mid)' }}>{detail.city}</div>}
                            </div>
                        </div>
                    </div>

                    {related.length > 1 && (
                        <div className="card rd-card">
                            <div className="rd-card-title">Solicitudes relacionadas</div>
                            <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '12px' }}>
                                Cadena de reprogramaciones de esta solicitud (de la original a la más reciente).
                            </div>
                            <div className="rd-timeline">
                                {related.map((h) => {
                                    const hst = STATUS_MAP[h.status] || { label: h.status, badge: '' };
                                    const isCurrent = h.id === currentId;
                                    return (
                                        <div className="rd-tl-item" key={h.id}>
                                            <div className={`rd-tl-dot ${isCurrent ? 'rd-tl-dot-current' : ''}`} />
                                            <div style={{ flex: 1 }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                                                    <span className={`badge ${hst.badge}`}>{hst.label}</span>
                                                    {isCurrent
                                                        ? <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--c-primary-d)' }}>Actual</span>
                                                        : <Link to={`/requests/${h.id}`} state={{ as: viewerAs }} className="link-more" style={{ fontSize: '12px' }}>Ver →</Link>}
                                                </div>
                                                <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '3px' }}>
                                                    Programada: {formatDate(h.scheduledDate)}
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>

                {/* Columna lateral */}
                <div>
                    {actionButtons.length > 0 && (
                        <div className="card rd-card">
                            <div className="rd-card-title">Acciones</div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {actionButtons}
                            </div>
                        </div>
                    )}

                    <div className="card rd-card">
                        <div className="rd-card-title">{counterpartyLabel}</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <div className="av av-md">
                                <Avatar src={getApiImageUrl(detail.counterpartyPhotoUrl)} initials={getInitials(detail.counterpartyName)} />
                            </div>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: 700, fontSize: '14px' }}>{detail.counterpartyName || '—'}</div>
                                {!isOfferer && detail.counterpartyId && (
                                    <Link to={`/offerers/${detail.counterpartyId}`} className="link-more" style={{ fontSize: '12px' }}>
                                        Ver perfil →
                                    </Link>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="card rd-card">
                        <div className="rd-card-title">Servicio</div>
                        <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '2px' }}>{detail.serviceTitle}</div>
                        <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '10px' }}>
                            {detail.priceHourly != null && <>desde {formatPrice(detail.priceHourly)}/h</>}
                            {detail.averageDurationMinutes != null && <> · ~{detail.averageDurationMinutes} min</>}
                        </div>
                        <button
                            className="btn btn-outline btn-sm btn-full"
                            onClick={() => navigate(`/services/${detail.serviceId}`)}
                        >
                            Ver servicio
                        </button>
                    </div>
                </div>
            </div>

            <ReviewModal
                open={reviewAction != null}
                onClose={() => setReviewAction(null)}
                title={reviewTitle}
                sub={ratesClient
                    ? `Puedes calificar a ${detail.counterpartyName || 'el cliente'}.`
                    : `Puedes calificar el servicio "${detail.serviceTitle}".`}
                ratingLabel={ratesClient ? 'Calificación del cliente' : 'Calificación del servicio'}
                reviewLabel="Reseña (opcional)"
                confirmLabel={
                    reviewAction === 'confirm' ? 'Confirmar servicio'
                        : reviewAction === 'complete' ? 'Marcar completado'
                            : 'Enviar calificación'}
                onConfirm={handleReviewConfirm}
            />

            {/* Cliente: reprogramación directa */}
            <Modal open={reschedOpen} onClose={closeDialogs}>
                <div className="modal-title">Reprogramar solicitud</div>
                <div className="modal-sub">Elige una nueva fecha y hora.</div>
                <DateTimeFields dateVal={dateVal} setDateVal={setDateVal} timeVal={timeVal} setTimeVal={setTimeVal} />
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={closeDialogs}>Cancelar</button>
                    <button className="btn btn-primary btn-full" disabled={!dateVal || acting} onClick={handleReschedule}>Confirmar reprogramación</button>
                </div>
            </Modal>

            {/* Oferente: propuesta de reprogramación */}
            <Modal open={proposalOpen} onClose={closeDialogs}>
                <div className="modal-title">Proponer reprogramación</div>
                <div className="modal-sub">El cliente recibirá la propuesta y podrá aceptarla o rechazarla.</div>
                <DateTimeFields dateVal={dateVal} setDateVal={setDateVal} timeVal={timeVal} setTimeVal={setTimeVal} />
                <div className="input-group">
                    <label className="label">Motivo (opcional)</label>
                    <textarea className="input" rows="2" value={reasonVal} onChange={(e) => setReasonVal(e.target.value)} placeholder="Explica por qué propones el cambio" />
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={closeDialogs}>Cancelar</button>
                    <button className="btn btn-primary btn-full" disabled={!dateVal || acting} onClick={handleProposal}>Enviar propuesta</button>
                </div>
            </Modal>
        </>
    );
}

function DateTimeFields({ dateVal, setDateVal, timeVal, setTimeVal }) {
    return (
        <>
            <div className="input-group">
                <label className="label">Nueva fecha</label>
                <input className="input" type="date" value={dateVal} min={new Date().toISOString().split('T')[0]} onChange={(e) => setDateVal(e.target.value)} />
            </div>
            <div className="input-group">
                <label className="label">Nueva hora</label>
                <select className="input" value={timeVal} onChange={(e) => setTimeVal(e.target.value)}>
                    {TIME_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
            </div>
        </>
    );
}

function Field({ label, value }) {
    return (
        <div className="rd-field">
            <div className="rd-field-label">{label}</div>
            <div className="rd-field-value">{value}</div>
        </div>
    );
}

export default RequestDetailPage;
