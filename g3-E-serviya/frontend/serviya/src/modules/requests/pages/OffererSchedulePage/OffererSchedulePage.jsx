import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, ToastContainer, useToast, OFFERER_NAV, offererAgendaApi, reportApi, requestApi } from '../../../../shared';
import { MonthCalendar } from '../../components/MonthCalendar/MonthCalendar';

const WEEKDAY_NAMES = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MONTH_NAMES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const STATUS_BADGE = {
    PENDING: { className: 'badge-warn', label: 'Pendiente aceptar' },
    ACCEPTED: { className: 'badge-success', label: 'Aceptada' },
    PRESUMABLY_COMPLETED: { className: 'badge-warn', label: 'Esperando confirmación' },
};

function dayKey(date) {
    // local date key, not UTC, so it matches what the user sees on the calendar
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}

function formatDayTitle(date) {
    const weekday = WEEKDAY_NAMES[date.getDay()];
    const month = MONTH_NAMES[date.getMonth()];
    return `${weekday.charAt(0).toUpperCase() + weekday.slice(1)} ${date.getDate()} de ${month}`;
}

// Groups a flat list of requests (each with a `scheduledDate`) into an
// array of { date, title, events } buckets, sorted chronologically.
function groupByDay(requests) {
    const buckets = new Map();

    for (const r of requests) {
        if (!r.scheduledDate) continue;
        const date = new Date(r.scheduledDate);
        const key = dayKey(date);

        if (!buckets.has(key)) {
            buckets.set(key, { date, title: formatDayTitle(date), events: [] });
        }

        const badge = STATUS_BADGE[r.status] ?? { className: 'badge-gray', label: r.status };
        buckets.get(key).events.push({
            id: r.id,
            hour: date.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit', hour12: true }),
            name: `Servicio #${r.serviceId}`,
            sub: `Cliente #${r.clientId}`,
            badgeClassName: badge.className,
            label: badge.label,
        });
    }

    return Array.from(buckets.values()).sort((a, b) => a.date - b.date);
}

/** Estados en los que ya no tiene sentido reportar: la solicitud esta cerrada. */
const TERMINAL_LABELS = ['Completada', 'Cancelada', 'Rechazada', 'No prestada'];

export function OffererSchedulePage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();

    const [requests, setRequests] = useState([]);
    const [selectedDate, setSelectedDate] = useState(null);

    // RF-073 (lado oferente): reporte de incumplimiento del cliente.
    const [reportOpen, setReportOpen] = useState(false);
    const [reportTarget, setReportTarget] = useState(null);
    const [reportCategory, setReportCategory] = useState('El cliente no estaba en casa');
    const [customCategory, setCustomCategory] = useState('');
    const [reportReason, setReportReason] = useState('');
    const [sendingReport, setSendingReport] = useState(false);

    const closeReport = () => {
        setReportOpen(false);
        setReportTarget(null);
        setReportCategory('El cliente no estaba en casa');
        setCustomCategory('');
        setReportReason('');
    };

    /**
     * Se manda SOLO el id de la solicitud: el backend deriva a quién se reporta desde la contraparte
     * de esa solicitud (aquí, el cliente). Mandar el reportedUserId permitiría incriminar a un tercero.
     */
    const submitReport = async () => {
        setSendingReport(true);
        try {
            await reportApi.createRequestReport({
                category: reportCategory,
                customCategory,
                reason: reportReason,
                requestId: reportTarget?.id,
            });
            closeReport();
            showToast('Reporte enviado. Un administrador lo revisará.', 'success');
        } catch (err) {
            showToast(err.message || 'No se pudo enviar el reporte', 'danger');
        } finally {
            setSendingReport(false);
        }
    };

    const loadAgenda = useCallback(() => {
        offererAgendaApi
            .getOffererAgenda()
            .then((data) => setRequests(data.content ?? []))
            .catch((err) => showToast(err.message || 'No se pudo cargar la agenda', 'error'));
    }, [showToast]);

    useEffect(() => { loadAgenda(); }, [loadAgenda]);

    const handleAccept = async (event) => {
        try {
            await requestApi.acceptRequest(event.id);
            showToast('Solicitud aceptada. Cliente notificado', 'success');
            loadAgenda();
        } catch (err) {
            showToast(err.message || 'No se pudo aceptar la solicitud', 'danger');
        }
    };

    const allDays = groupByDay(requests);
    const visibleDays = selectedDate
        ? allDays.filter((d) => dayKey(d.date) === dayKey(selectedDate))
        : allDays;

    return (
        <DashboardLayout sections={OFFERER_NAV} avatar="CM">
            <div className="ph"><h1>Mi agenda</h1><p>Servicios programados para atender</p></div>
            <div className="g2" style={{ gap: '20px', alignItems: 'start' }}>
                <MonthCalendar
                    events={requests}
                    selectedDate={selectedDate}
                    onDayClick={(date) =>
                        setSelectedDate((prev) =>
                            prev && dayKey(prev) === dayKey(date) ? null : date
                        )
                    }
                />
                <div>
                    {selectedDate && (
                        <button
                            className="btn btn-ghost btn-sm"
                            style={{ marginBottom: '10px' }}
                            onClick={() => setSelectedDate(null)}
                        >
                            Ver todos los próximos servicios
                        </button>
                    )}

                    {visibleDays.length === 0 && (
                        <p style={{ fontSize: '13px', color: 'var(--c-mid)' }}>
                            {selectedDate ? 'No tienes servicios este día.' : 'No tienes servicios próximos.'}
                        </p>
                    )}

                    {visibleDays.map((d) => (
                        <React.Fragment key={dayKey(d.date)}>
                            <div className="ev-daytitle">{d.title}</div>
                            {d.events.map((e) => (
                                <div className="ev-item" key={e.id}>
                                    <div className="ev-time">{e.hour}</div>
                                    <div style={{ flex: 1 }}>
                                        <div style={{ fontSize: '13px', fontWeight: 700 }}>{e.name}</div>
                                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '2px' }}>{e.sub}</div>
                                        <div style={{ marginTop: '7px', display: 'flex', gap: '5px', flexWrap: 'wrap', alignItems: 'center' }}>
                                            <span className={`badge ${e.badgeClassName}`}>{e.label}</span>
                                            <button
                                                className="btn btn-ghost btn-sm"
                                                style={{ border: '1px solid var(--c-border)' }}
                                                onClick={() => navigate(`/requests/${e.id}`, { state: { as: 'offerer' } })}
                                            >
                                                Ver detalle
                                            </button>
                                            {e.label === 'Pendiente aceptar' && (
                                                <button
                                                    className="btn btn-success btn-sm"
                                                    onClick={() => handleAccept(e)}
                                                >
                                                    <Icon name="check" size={13} />
                                                    Aceptar
                                                </button>
                                            )}
                                            {/* RF-073: el oferente también puede reportar incumplimiento
                                                (p. ej. el cliente no estaba en casa). Solo mientras la
                                                solicitud sigue viva: una ya cerrada no se reporta por aquí. */}
                                            {!TERMINAL_LABELS.includes(e.label) && (
                                                <button
                                                    className="btn btn-ghost btn-sm"
                                                    style={{ color: 'var(--c-danger)' }}
                                                    onClick={() => { setReportTarget(e); setReportOpen(true); }}
                                                >
                                                    <Icon name="alertTriangle" size={13} />
                                                    Reportar
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </React.Fragment>
                    ))}
                </div>
            </div>
            {/* RF-073: mismo flujo que el del cliente, pero reportando al cliente. */}
            <Modal open={reportOpen} onClose={closeReport}>
                <div className="modal-title">Reportar incumplimiento</div>
                <div className="modal-sub">
                    {reportTarget
                        ? <>Solicitud <strong>#{reportTarget.id}</strong> · {reportTarget.sub}. Un administrador revisará el caso.</>
                        : 'Un administrador revisará el caso.'}
                </div>

                <div className="input-group">
                    <label className="label">Categoría</label>
                    <select className="input" value={reportCategory} onChange={(ev) => setReportCategory(ev.target.value)}>
                        <option>El cliente no estaba en casa</option>
                        <option>No permitió realizar el servicio</option>
                        <option>Comportamiento inapropiado</option>
                        <option>Otra</option>
                    </select>
                </div>
                {reportCategory === 'Otra' && (
                    <div className="input-group">
                        <label className="label">Categoría personalizada</label>
                        <input className="input" value={customCategory}
                            onChange={(ev) => setCustomCategory(ev.target.value)} placeholder="Escribe la categoría" />
                    </div>
                )}
                <div className="input-group">
                    <label className="label">Descripción</label>
                    <textarea className="input" rows="3" value={reportReason}
                        onChange={(ev) => setReportReason(ev.target.value)} placeholder="Describe lo que ocurrió..." />
                </div>

                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={closeReport}>Cancelar</button>
                    <button className="btn btn-danger btn-full" disabled={!reportReason.trim() || sendingReport}
                        onClick={submitReport}>
                        {sendingReport ? 'Enviando…' : 'Enviar reporte'}
                    </button>
                </div>
            </Modal>

            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default OffererSchedulePage;