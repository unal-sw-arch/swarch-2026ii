import { useState, useEffect, useCallback } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, ToastContainer, useToast, OFFERER_NAV } from '../../../../shared';
import { proposalApi } from '../../../../shared/api';
import { formatDate, getInitials } from '../../utils';

import '../ReschedulesPage/ReschedulesPage.css';

const PROPOSAL_STATUS_MAP = {
    PENDING:    { label: 'Pendiente',   badge: 'badge-warn' },
    ACCEPTED:   { label: 'Aceptada',    badge: 'badge-success' },
    REJECTED:   { label: 'Rechazada',   badge: 'badge-danger' },
    CANCELLED:  { label: 'Cancelada',   badge: 'badge-gray' },
    SUPERSEDED: { label: 'Reemplazada', badge: 'badge-gray' },
};

/**
 * Propuestas de reprogramación que el OFERENTE ha ENVIADO a sus clientes. Muestra el estado de
 * cada una y permite cancelar las pendientes. (El cliente las acepta/rechaza desde /reschedules.)
 */
export function OffererReschedulesPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [pending, setPending] = useState([]);
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    // Detalle de propuesta (modal): misma lógica que el cliente, con las acciones del oferente
    // (cancelar la propuesta pendiente + ver la solicitud).
    const [detail, setDetail] = useState(null);
    const [detailOpen, setDetailOpen] = useState(false);
    const [acting, setActing] = useState(false);

    const fetchProposals = useCallback(async () => {
        setLoading(true);
        try {
            const [pendingRes, historyRes] = await Promise.all([
                proposalApi.getSent({ page: 0, size: 20, statuses: 'PENDING' }),
                proposalApi.getSent({ page: 0, size: 50 }),
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

    const handleCancel = async (proposal) => {
        if (!window.confirm('¿Cancelar esta propuesta de reprogramación?')) return;
        setActing(true);
        try {
            await proposalApi.cancelProposal(proposal.proposalId);
            showToast('Propuesta cancelada', 'success');
            setDetailOpen(false);
            fetchProposals();
        } catch (err) {
            showToast(err.message || 'No se pudo cancelar la propuesta', 'danger');
        } finally {
            setActing(false);
        }
    };

    const openDetail = (proposalId) => {
        setDetail(null);
        setDetailOpen(true);
        proposalApi.getProposalById(proposalId)
            .then(setDetail)
            .catch(() => showToast('No se pudo cargar el detalle de la propuesta', 'danger'));
    };

    return (
        <DashboardLayout sections={OFFERER_NAV}>
            <div className="ph"><h1>Propuestas de reprogramación</h1><p>Reprogramaciones que has propuesto a tus clientes</p></div>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando propuestas...</div>
            ) : (
                <>
                    {pending.length > 0 && (
                        <>
                            <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Pendientes de respuesta <span className="badge badge-warn">{pending.length}</span></div>
                            {pending.map((p) => (
                                <div className="resched-pending" key={p.proposalId}>
                                    <div className="rp-head">
                                        <div className="av av-md">{getInitials(p.counterpartyName)}</div>
                                        <div><div style={{ fontWeight: 700, fontSize: '14px' }}>{p.counterpartyName}</div><div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{p.serviceTitle}</div></div>
                                        <span className="badge badge-warn" style={{ marginLeft: 'auto' }}>Esperando al cliente</span>
                                    </div>
                                    <div className="rp-change">
                                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '6px' }}>Propones cambiar de:</div>
                                        <div className="rp-dates">
                                            <div className="rp-date"><strong>{formatDate(p.originalScheduledDate)}</strong></div>
                                            <Icon name="arrowRight" size={16} style={{ color: 'var(--c-warn)' }} />
                                            <div className="rp-date rp-date-new"><strong>{formatDate(p.proposedDate)}</strong></div>
                                        </div>
                                        {p.reason && <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '10px', fontStyle: 'italic' }}>"{p.reason}"</div>}
                                    </div>
                                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                        <button className="btn btn-danger" onClick={() => handleCancel(p)}><Icon name="close" size={15} />Cancelar propuesta</button>
                                        <button className="btn btn-ghost" style={{ border: '1px solid var(--c-border)' }} onClick={() => openDetail(p.proposalId)}><Icon name="reschedule" size={15} />Ver detalle</button>
                                    </div>
                                </div>
                            ))}
                        </>
                    )}

                    {pending.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>No tienes propuestas pendientes</div>
                    )}

                    {history.length > 0 && (
                        <>
                            <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Historial de propuestas</div>
                            <div className="tbl-wrap">
                                <table>
                                    <thead><tr><th>Servicio</th><th>Cliente</th><th>Fecha original</th><th>Fecha propuesta</th><th>Estado</th><th></th></tr></thead>
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

            {/* Detalle de propuesta enviada: mismos datos que ve el cliente + acciones del oferente */}
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
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '6px' }}>Propones cambiar de:</div>
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
                                onClick={() => navigate(`/requests/${detail.requestId}`, { state: { as: 'offerer' } })}
                            >
                                <Icon name="tasks" size={14} />Ver solicitud
                            </button>

                            {isPending ? (
                                <button className="btn btn-danger btn-full" disabled={acting} onClick={() => handleCancel(detail)}>
                                    <Icon name="close" size={15} />Cancelar propuesta
                                </button>
                            ) : (
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

export default OffererReschedulesPage;
