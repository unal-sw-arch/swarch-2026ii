import { useEffect, useState, useCallback } from "react";
import { AdminNavbar } from '../../components/AdminNavbar/AdminNavbar';
import { AdminSidebar } from '../../components/AdminSidebar/AdminSidebar';
import { ReportCard } from '../../components/ReportCard/ReportCard';
import { ManagementModal } from '../../components/ManagementModal/ManagementModal';
import { ToastContainer } from '../../../../shared/components/Toast/Toast';
import { useToast } from '../../../../shared/hooks/useToast';
import { reportApi, moderationApi, userApi } from '../../../../shared/api';
import { Modal, Avatar, Icon, getApiImageUrl } from '../../../../shared';

import './AdminReportsPage.css';

const REPORT_TYPE_LABELS = {
    REQUEST: 'Solicitud',
    SERVICE_FEEDBACK: 'Reseña servicio',
    CLIENT_FEEDBACK: 'Reseña cliente'
};

const PRIORITY_LABELS = {
    LOW: 'Baja',
    MEDIUM: 'Media',
    HIGH: 'Alta',
    CRITICAL: 'Crítica'
};

const STATUS_LABELS = { PENDING: 'Pendiente', RESOLVED: 'Resuelto', CLOSED: 'Cerrado' };
const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' }) : '—');

function PartyBlock({ label, party }) {
    if (!party) return null;
    return (
        <div>
            <div style={{ fontSize: '11px', color: 'var(--c-soft)', textTransform: 'uppercase', marginBottom: '4px' }}>{label}</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <div className="av av-sm">
                    <Avatar src={getApiImageUrl(party.photoUrl)} initials={getInitials(party.fullName)} />
                </div>
                <span style={{ fontSize: '13px', fontWeight: 600 }}>{party.fullName || `Usuario #${party.userId}`}</span>
            </div>
        </div>
    );
}

const getInitials = (name = '') => {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return 'U';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
};

export function AdminReportsPage() {
    const { toasts, showToast } = useToast();
    const [activeTab, setActiveTab] = useState('Todos');
    const [selectedReport, setSelectedReport] = useState(null);
    const [reports, setReports] = useState([]);
    const [loading, setLoading] = useState(true);
    // Detalle enriquecido (GET /reports/{id}) + historial de acciones (GET /reports/{id}/actions).
    const [detailOpen, setDetailOpen] = useState(false);
    const [detail, setDetail] = useState(null);
    const [detailActions, setDetailActions] = useState([]);

    useEffect(() => {
        const loadReports = async () => {
            try {
                const data = await reportApi.getAll();
                const mapped = await Promise.all((data.content ?? []).map(async (report) => {
                    const [reporterName, reportedName] = await Promise.all([
                        userApi.getDisplayName(report.reporterId),
                        userApi.getDisplayName(report.reportedUserId),
                    ]);

                    return {
                        id: `#RPT-${String(report.id).padStart(3, '0')}`,
                        rawId: report.id,
                        rawReportType: report.reportType,
                        date: new Date(report.createdAt).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }),
                        type: REPORT_TYPE_LABELS[report.reportType] ?? report.reportType,
                        priority: PRIORITY_LABELS[report.priority] ?? report.priority,
                        status: report.status === 'PENDING' ? 'Pendiente' : report.status === 'RESOLVED' ? 'Resuelto' : 'Cerrado',
                        reporterInitials: getInitials(reporterName),
                        reporterName,
                        reportedInitials: getInitials(reportedName),
                        reportedName,
                        reason: report.category,
                        description: report.reason,
                        category: report.category,
                        rawStatus: report.status,
                    };
                }));

                setReports(mapped);
            } catch {
                showToast('No fue posible cargar los reportes', 'danger');
            } finally {
                setLoading(false);
            }
        };

        loadReports();
    }, [showToast]);

    const handleOpenManagement = (report) => {
        setSelectedReport(report);
    };

    const openDetail = (report) => {
        setDetail(null);
        setDetailActions([]);
        setDetailOpen(true);
        reportApi.getById(report.rawId)
            .then(setDetail)
            .catch(() => showToast('No se pudo cargar el detalle del reporte', 'danger'));
        reportApi.getActions(report.rawId)
            .then((a) => setDetailActions(Array.isArray(a) ? a : []))
            .catch(() => { /* el historial es complementario */ });
    };

    const handleExecuteManagement = useCallback(async (action) => {
        if (!selectedReport) return;
        const rawId = selectedReport.rawId;
        try {
            switch (action) {
                case 'warn':
                    await moderationApi.warnUser(rawId);
                    showToast('Advertencia enviada y reporte cerrado', 'success');
                    break;
                case 'ban':
                    await moderationApi.banUser(rawId);
                    showToast('Usuario baneado y reporte cerrado', 'success');
                    break;
                case 'revert':
                    await moderationApi.revertFeedback(rawId);
                    showToast('Reseña revertida y reporte cerrado', 'success');
                    break;
                case 'mark_not_provided':
                    await moderationApi.markNotProvided(rawId);
                    showToast('Solicitud marcada como no prestada', 'success');
                    break;
                case 'close':
                default:
                    await moderationApi.closeReport(rawId);
                    showToast('Reporte cerrado sin penalización', 'success');
                    break;
            }
            setReports((prev) => prev.filter((r) => r.rawId !== rawId));
        } catch (err) {
            showToast(err.message || 'No fue posible ejecutar la acción', 'danger');
        } finally {
            setSelectedReport(null);
        }
    }, [selectedReport, showToast]);

    const handleDeleteFeedback = useCallback(async (report) => {
        try {
            if (report.rawReportType === 'SERVICE_FEEDBACK') {
                await moderationApi.revertFeedback(report.rawId);
            } else if (report.rawReportType === 'CLIENT_FEEDBACK') {
                await moderationApi.revertFeedback(report.rawId);
            }
            showToast('Reseña eliminada correctamente', 'success');
            setReports((prev) => prev.filter((r) => r.rawId !== report.rawId));
        } catch (err) {
            showToast(err.message || 'No fue posible eliminar la reseña', 'danger');
        }
    }, [showToast]);

    return (
        <>
            <AdminNavbar />
            
            <div className="admin-layout-container">
                <AdminSidebar />

                <main className="main-content">
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '10px' }}>
                        <div className="ph">
                            <h1>Gestión de reportes</h1>
                            <p>Modera los reportes enviados por usuarios</p>
                        </div>
                    </div>

                    <div style={{ display: 'flex', gap: '8px', marginBottom: '18px', flexWrap: 'wrap' }}>
                        <div className={`chip ${activeTab === 'Todos' ? 'active' : ''}`} onClick={() => setActiveTab('Todos')}>Todos ({reports.length})</div>
                        <div className={`chip ${activeTab === 'Pendientes' ? 'active' : ''}`} onClick={() => setActiveTab('Pendientes')}>Pendientes ({reports.filter((r) => r.rawStatus === 'PENDING').length})</div>
                        <div className={`chip ${activeTab === 'Resueltos' ? 'active' : ''}`} onClick={() => setActiveTab('Resueltos')}>Resueltos ({reports.filter((r) => r.rawStatus === 'RESOLVED').length})</div>
                    </div>

                    <div>
                        {loading ? <div className="card">Cargando reportes...</div> : reports.filter((report) => activeTab === 'Todos' || (activeTab === 'Pendientes' ? report.rawStatus === 'PENDING' : report.rawStatus === 'RESOLVED')).map((report) => (
                            <ReportCard
                                key={report.rawId}
                                report={report}
                                onManage={handleOpenManagement}
                                onDetail={() => openDetail(report)}
                                onDelete={() => handleDeleteFeedback(report)}
                            />
                        ))}
                    </div>
                </main>
            </div>

            {/* Modal de Gestión Controlado */}
            {selectedReport && (
                <ManagementModal 
                    report={selectedReport} 
                    onClose={() => setSelectedReport(null)} 
                    onExecute={handleExecuteManagement}
                />
            )}

            {/* Detalle enriquecido del reporte (partes reales + payload del subtipo + historial) */}
            <Modal open={detailOpen} onClose={() => setDetailOpen(false)} maxWidth={560}>
                {!detail ? (
                    <div style={{ textAlign: 'center', padding: '24px', color: 'var(--c-soft)' }}>Cargando detalle...</div>
                ) : (
                    <>
                        <div className="modal-title">Detalle del reporte</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap', marginBottom: '14px' }}>
                            <span className="badge badge-primary">{REPORT_TYPE_LABELS[detail.reportType] ?? detail.reportType}</span>
                            <span className="badge badge-gray">{PRIORITY_LABELS[detail.priority] ?? detail.priority}</span>
                            <span className="badge badge-warn">{STATUS_LABELS[detail.status] ?? detail.status}</span>
                            <span style={{ fontSize: '12px', color: 'var(--c-soft)', marginLeft: 'auto' }}>{fmtDate(detail.createdAt)}</span>
                        </div>

                        <div style={{ display: 'flex', gap: '18px', flexWrap: 'wrap', marginBottom: '14px' }}>
                            <PartyBlock label="Reportante" party={detail.reporter} />
                            <PartyBlock label="Reportado" party={detail.reported} />
                        </div>

                        <div style={{ background: 'var(--c-bg-s)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', color: 'var(--c-mid)', marginBottom: '12px' }}>
                            <strong>Motivo:</strong> {detail.category}<br />
                            <strong>Descripción:</strong> {detail.reason}
                        </div>

                        {detail.request && (
                            <div style={{ border: '1px solid var(--c-border)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', marginBottom: '12px' }}>
                                <div style={{ fontWeight: 700, marginBottom: '4px' }}>Solicitud reportada</div>
                                <div>{detail.request.serviceTitle} · {fmtDate(detail.request.scheduledDate)}</div>
                                <div style={{ color: 'var(--c-mid)' }}>
                                    Estado: {detail.request.status}
                                    {detail.request.city ? ` · ${detail.request.city}` : ''}
                                    {detail.request.requestedPrice != null ? ` · $${Number(detail.request.requestedPrice).toLocaleString('es-CO')}` : ''}
                                </div>
                            </div>
                        )}

                        {detail.feedback && (
                            <div style={{ border: '1px solid var(--c-border)', borderRadius: 'var(--r-lg)', padding: '10px 12px', fontSize: '12px', marginBottom: '12px' }}>
                                <div style={{ fontWeight: 700, marginBottom: '4px' }}>Reseña reportada</div>
                                <div>
                                    {detail.feedback.rating != null && <>{'★'.repeat(detail.feedback.rating)}{'☆'.repeat(5 - detail.feedback.rating)} · </>}
                                    {fmtDate(detail.feedback.createdAt)}
                                </div>
                                {detail.feedback.comment && <div style={{ marginTop: '4px', color: 'var(--c-mid)', fontStyle: 'italic' }}>"{detail.feedback.comment}"</div>}
                            </div>
                        )}

                        <div style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>Historial de acciones ({detailActions.length})</div>
                        {detailActions.length > 0 ? detailActions.map((a, i) => (
                            <div key={i} style={{ fontSize: '12px', color: 'var(--c-mid)', paddingBottom: '8px', marginBottom: '8px', borderBottom: i < detailActions.length - 1 ? '1px solid var(--c-border-s)' : 'none' }}>
                                <div><Icon name="shield" size={12} /> {a.actionDescription}</div>
                                <div style={{ fontSize: '11px', color: 'var(--c-soft)' }}>Admin #{a.adminId} · {fmtDate(a.createdAt)}</div>
                            </div>
                        )) : (
                            <div style={{ fontSize: '12px', color: 'var(--c-soft)' }}>Sin acciones registradas todavía.</div>
                        )}

                        <button className="btn btn-ghost btn-full" style={{ marginTop: '14px' }} onClick={() => setDetailOpen(false)}>Cerrar</button>
                    </>
                )}
            </Modal>

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default AdminReportsPage;