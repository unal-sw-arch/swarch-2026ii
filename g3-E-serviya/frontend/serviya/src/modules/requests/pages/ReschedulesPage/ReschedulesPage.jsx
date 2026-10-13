import { useState, useEffect, useCallback } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, ToastContainer, useToast, CLIENT_NAV } from '../../../../shared';
import { proposalApi, requestApi } from '../../../../shared/api';
import { formatDate, getInitials } from '../../utils';

const TIME_OPTIONS = [
    ['09:00', '9:00 AM'], ['10:00', '10:00 AM'], ['11:00', '11:00 AM'],
    ['14:00', '2:00 PM'], ['15:00', '3:00 PM'], ['16:00', '4:00 PM'],
];

import './ReschedulesPage.css';

const PROPOSAL_STATUS_MAP = {
    PENDING:    { label: 'Pendiente',  badge: 'badge-warn' },
    ACCEPTED:   { label: 'Aceptada',   badge: 'badge-success' },
    REJECTED:   { label: 'Rechazada',  badge: 'badge-danger' },
    CANCELLED:  { label: 'Cancelada',  badge: 'badge-gray' },
    SUPERSEDED: { label: 'Reemplazada', badge: 'badge-gray' },
};

export function ReschedulesPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [pending, setPending] = useState([]);
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    // Detalle de propuesta (modal) + tercera acción del cliente: elegir nueva fecha (supera la propuesta).
    const [detail, setDetail] = useState(null);
    const [detailOpen, setDetailOpen] = useState(false);
    const [acting, setActing] = useState(false);
    const [freeMode, setFreeMode] = useState(false);
    const [freeDate, setFreeDate] = useState('');
    const [freeTime, setFreeTime] = useState('09:00');

    const fetchProposals = useCallback(async () => {
        setLoading(true);
        try {
            const [pendingRes, historyRes] = await Promise.all([
                proposalApi.getReceived({ page: 0, size: 20, statuses: 'PENDING' }),
                proposalApi.getReceived({ page: 0, size: 50 }),
            ]);
            setPending(pendingRes.content ?? []);
            setHistory((historyRes.content ?? []).filter(p => p.status !== 'PENDING'));
        } catch {
            showToast('No se pudieron cargar las propuestas', 'danger');
        } finally {
            setLoading(false);
        }
    }, [showToast]);

    useEffect(() => { fetchProposals(); }, [fetchProposals]);

    const handleAccept = async (proposal) => {
        try {
            await proposalApi.acceptProposal(proposal.proposalId);
            showToast('Propuesta aceptada. Servicio reprogramado', 'success');
            setDetailOpen(false);
            fetchProposals();
        } catch (err) {
            showToast(err.message || 'No se pudo aceptar la propuesta', 'danger');
        }
    };

    const handleReject = async (proposal) => {
        try {
            await proposalApi.rejectProposal(proposal.proposalId);
            showToast('Propuesta rechazada', 'success');
            setDetailOpen(false);
            fetchProposals();
        } catch (err) {
            showToast(err.message || 'No se pudo rechazar la propuesta', 'danger');
        }
    };

    const openDetail = (proposalId) => {
        setDetail(null);
        setFreeMode(false);
        setFreeDate('');
        setFreeTime('09:00');
        setDetailOpen(true);
        proposalApi.getProposalById(proposalId)
            .then(setDetail)
            .catch(() => showToast('No se pudo cargar el detalle de la propuesta', 'danger'));
    };

    // Tercera vía: el cliente elige libremente una nueva fecha. rescheduleRequest crea una nueva
    // solicitud y "supera" (SUPERSEDED) la propuesta pendiente del oferente.
    const handleFreeReschedule = async () => {
        if (!detail || !freeDate) return;
        setActing(true);
        try {
            await requestApi.rescheduleRequest(detail.requestId, { newDate: `${freeDate}T${freeTime}:00` });
            showToast('Elegiste una nueva fecha; la propuesta fue superada', 'success');
            setDetailOpen(false);
            fetchProposals();
        } catch (err) {
            showToast(err.message || 'No se pudo reprogramar', 'danger');
        } finally {
            setActing(false);
        }
    };

    return (
        <DashboardLayout sections={CLIENT_NAV} avatar="JP">
            <div className="ph"><h1>Propuestas de reprogramación</h1><p>Responde a las propuestas enviadas por los oferentes</p></div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando propuestas...</div>
            ) : (
                <>
                    {pending.length > 0 && (
                        <>
                            <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Pendientes <span className="badge badge-warn">{pending.length}</span></div>
                            {pending.map((p) => (
                                <div className="resched-pending" key={p.proposalId}>
                                    <div className="rp-head">
                                        <div className="av av-md">{(p.counterpartyName || '??').split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()}</div>
                                        <div><div style={{ fontWeight: 700, fontSize: '14px' }}>{p.counterpartyName}</div><div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{p.serviceTitle}</div></div>
                                        <span className="badge badge-warn" style={{ marginLeft: 'auto' }}>Requiere acción</span>
                                    </div>
                                    <div className="rp-change">
                                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '6px' }}>Propone cambiar de:</div>
                                        <div className="rp-dates">
                                            <div className="rp-date"><strong>{formatDate(p.originalScheduledDate)}</strong></div>
                                            <Icon name="arrowRight" size={16} style={{ color: 'var(--c-warn)' }} />
                                            <div className="rp-date rp-date-new"><strong>{formatDate(p.proposedDate)}</strong></div>
                                        </div>
                                        {p.reason && <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '10px', fontStyle: 'italic' }}>"{p.reason}"</div>}
                                    </div>
                                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                        <button className="btn btn-success" onClick={() => handleAccept(p)}><Icon name="check" size={15} />Aceptar propuesta</button>
                                        <button className="btn btn-danger" onClick={() => handleReject(p)}><Icon name="close" size={15} />Rechazar</button>
                                        <button className="btn btn-ghost" style={{ border: '1px solid var(--c-border)' }} onClick={() => openDetail(p.proposalId)}><Icon name="reschedule" size={15} />Ver detalle / otra fecha</button>
                                    </div>
                                </div>
                            ))}
                        </>
                    )}

                    {pending.length === 0 && !loading && (
                        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>No tienes propuestas pendientes</div>
                    )}

                    {history.length > 0 && (
                        <>
                            <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Historial de propuestas</div>
                            <div className="tbl-wrap">
                                <table>
                                    <thead><tr><th>Servicio</th><th>Oferente</th><th>Fecha original</th><th>Fecha propuesta</th><th>Estado</th><th></th></tr></thead>
                                    <tbody>
                                        {history.map((h) => {
                                            const st = PROPOSAL_STATUS_MAP[h.status] || { label: h.status, badge: '' };
                                            return (
                                                <tr key={h.proposalId}>
                                                    <td>{h.serviceTitle}</td>
                                                    <td>{h.counterpartyName}</td>
                                                    <td style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{formatDate(h.originalScheduledDate)}</td>
                                                    <td style={{ fontSize: '12px' }}>{formatDate(h.proposedDate)}</td>
                                                    <td><span className={`badge ${st.badge}`}>{st.label}</span></td>
                                                    <td><button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => openDetail(h.proposalId)}>Ver</button></td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}
                </>
            )}
            {/* Detalle de propuesta + 3 acciones del cliente */}
            <Modal open={detailOpen} onClose={() => setDetailOpen(false)}>
                {!detail ? (
                    <div style={{ textAlign: 'center', padding: '20px', color: 'var(--c-soft)' }}>Cargando detalle...</div>
                ) : (() => {
                    const st = PROPOSAL_STATUS_MAP[detail.status] || { label: detail.status, badge: '' };
                    const isPending = detail.status === 'PENDING';
                    return (
                        <>
                            <div className="modal-title">Propuesta de reprogramación</div>
                            <div className="rp-head" style={{ marginBottom: '12px' }}>
                                <div className="av av-md">{getInitials(detail.counterpartyName)}</div>
                                <div>
                                    <div style={{ fontWeight: 700, fontSize: '14px' }}>{detail.counterpartyName}</div>
                                    <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{detail.serviceTitle}{detail.categoryName ? ` · ${detail.categoryName}` : ''}</div>
                                </div>
                                <span className={`badge ${st.badge}`} style={{ marginLeft: 'auto' }}>{st.label}</span>
                            </div>

                            <div className="rp-change" style={{ marginBottom: '12px' }}>
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '6px' }}>Propone cambiar de:</div>
                                <div className="rp-dates">
                                    <div className="rp-date"><strong>{formatDate(detail.originalScheduledDate)}</strong></div>
                                    <Icon name="arrowRight" size={16} style={{ color: 'var(--c-warn)' }} />
                                    <div className="rp-date rp-date-new"><strong>{formatDate(detail.proposedDate)}</strong></div>
                                </div>
                                {detail.reason && <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '10px', fontStyle: 'italic' }}>"{detail.reason}"</div>}
                            </div>

                            <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '12px' }}>
                                {detail.addressLabel && <div><strong>Dirección:</strong> {detail.addressLabel}</div>}
                                {detail.requestedPrice != null && <div><strong>Precio:</strong> ${Number(detail.requestedPrice).toLocaleString('es-CO')}</div>}
                                <div><strong>Enviada:</strong> {formatDate(detail.createdAt)}</div>
                            </div>

                            <button
                                className="btn btn-outline btn-sm btn-full"
                                style={{ marginBottom: '14px' }}
                                onClick={() => navigate(`/requests/${detail.requestId}`, { state: { as: 'client' } })}
                            >
                                <Icon name="tasks" size={14} />Ver solicitud
                            </button>

                            {isPending && !freeMode && (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    <button className="btn btn-success btn-full" disabled={acting} onClick={() => handleAccept(detail)}><Icon name="check" size={15} />Aceptar la fecha propuesta</button>
                                    <button className="btn btn-outline btn-full" disabled={acting} onClick={() => setFreeMode(true)}><Icon name="reschedule" size={15} />Elegir otra fecha</button>
                                    <button className="btn btn-danger btn-full" disabled={acting} onClick={() => handleReject(detail)}><Icon name="close" size={15} />Rechazar</button>
                                </div>
                            )}

                            {isPending && freeMode && (
                                <div>
                                    <div className="modal-sub">Elige tu propia fecha y hora. Esto reemplaza (supera) la propuesta del oferente.</div>
                                    <div className="input-group">
                                        <label className="label">Nueva fecha</label>
                                        <input className="input" type="date" value={freeDate} min={new Date().toISOString().split('T')[0]} onChange={(e) => setFreeDate(e.target.value)} />
                                    </div>
                                    <div className="input-group">
                                        <label className="label">Nueva hora</label>
                                        <select className="input" value={freeTime} onChange={(e) => setFreeTime(e.target.value)}>
                                            {TIME_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                                        </select>
                                    </div>
                                    <div style={{ display: 'flex', gap: '8px' }}>
                                        <button className="btn btn-ghost btn-full" onClick={() => setFreeMode(false)}>Volver</button>
                                        <button className="btn btn-primary btn-full" disabled={!freeDate || acting} onClick={handleFreeReschedule}>Confirmar nueva fecha</button>
                                    </div>
                                </div>
                            )}

                            {!isPending && (
                                <button className="btn btn-ghost btn-full" onClick={() => setDetailOpen(false)}>Cerrar</button>
                            )}
                        </>
                    );
                })()}
            </Modal>

            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default ReschedulesPage;
