import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, CLIENT_NAV, ToastContainer, useToast, clientAgendaApi } from '../../../../shared';
import { MonthCalendar } from '../../components/MonthCalendar/MonthCalendar';

const WEEKDAY_NAMES = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MONTH_NAMES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const STATUS_BADGE = {
    PENDING: { className: 'badge-warn', label: 'Pendiente' },
    ACCEPTED: { className: 'badge-success', label: 'Confirmada' },
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
            requestId: r.id,
            serviceId: r.serviceId,
            hour: date.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit', hour12: true }),
            name: `Servicio #${r.serviceId}`,
            sub: `Oferente #${r.offererId}`,
            badgeClassName: badge.className,
            label: badge.label,
            showDetail: r.status === 'PENDING',
        });
    }

    return Array.from(buckets.values()).sort((a, b) => a.date - b.date);
}

export function ClientSchedulePage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();

    const [requests, setRequests] = useState([]);
    const [selectedDate, setSelectedDate] = useState(null);

    useEffect(() => {
        clientAgendaApi
            .getClientAgenda()
            .then((data) => {
                setRequests(data.content ?? []);
            })
            .catch((err) => {
                showToast(err.message || 'No se pudo cargar la agenda', 'error');
            });
    }, []);

    const allDays = groupByDay(requests);
    const visibleDays = selectedDate
        ? allDays.filter((d) => dayKey(d.date) === dayKey(selectedDate))
        : allDays;

    return (
        <DashboardLayout sections={CLIENT_NAV} avatar="JP">
            <div className="ph"><h1>Mi agenda</h1><p>Visualiza y organiza tus servicios programados</p></div>
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
                                        <div style={{ marginTop: '7px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                                            <span className={`badge ${e.badgeClassName}`}>{e.label}</span>
                                            {e.showDetail && (
                                                <a
                                                    style={{ fontSize: '11px', color: 'var(--c-primary)', cursor: 'pointer', fontWeight: 600 }}
                                                    onClick={() => navigate(`/requests/${e.requestId}`, { state: { as: 'client' } })}
                                                >
                                                    Ver detalle →
                                                </a>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </React.Fragment>
                    ))}
                </div>
            </div>
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default ClientSchedulePage;
