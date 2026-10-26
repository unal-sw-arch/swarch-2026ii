import { useState } from 'react';
import { Icon } from '../../../../shared';

import './MonthCalendar.css';

const WEEK = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

const MONTH_NAMES = [
    'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

// JS Date.getDay() returns 0=Sunday..6=Saturday. Our week row starts on
// Monday (see WEEK above), so we remap to 0=Monday..6=Sunday.
function mondayIndex(jsDay) {
    return (jsDay + 6) % 7;
}

// Builds a full grid (always a multiple of 7 cells) for the given
// year/month, padding with the previous/next month's days, and marks
// which cells are "today", "selected", or have at least one event.
function buildCells(year, month, eventDayNumbers, selectedDay) {
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const daysInPrevMonth = new Date(year, month, 0).getDate();
    const leading = mondayIndex(new Date(year, month, 1).getDay());

    const today = new Date();
    const isCurrentMonth = today.getFullYear() === year && today.getMonth() === month;

    const cells = [];

    for (let i = leading - 1; i >= 0; i--) {
        cells.push({ key: `prev-${i}`, d: daysInPrevMonth - i, other: true });
    }

    for (let d = 1; d <= daysInMonth; d++) {
        cells.push({
            key: `cur-${d}`,
            d,
            other: false,
            today: isCurrentMonth && d === today.getDate(),
            selected: d === selectedDay,
            event: eventDayNumbers.has(d),
        });
    }

    let nextDay = 1;
    while (cells.length % 7 !== 0) {
        cells.push({ key: `next-${nextDay}`, d: nextDay, other: true });
        nextDay++;
    }

    return cells;
}

/**
 * MonthCalendar — month grid with prev/next navigation.
 *
 * @param {Array} [events] - items with a `scheduledDate` ISO string field
 *                           (e.g. the `days` array from the agenda API).
 *                           Days matching the currently viewed month get
 *                           an event dot.
 * @param {Date|null} [selectedDate] - the currently selected day (controlled
 *                           by the parent), highlighted if it falls in the
 *                           viewed month.
 * @param {Function} [onDayClick] - called with a `Date` (set to local
 *                           midnight) when the user clicks a day belonging
 *                           to the current month. Days from adjacent months
 *                           are not clickable.
 */
export function MonthCalendar({ events = [], selectedDate = null, onDayClick }) {
    const [viewDate, setViewDate] = useState(new Date());

    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();

    const eventDayNumbers = new Set(
        events
            .map((e) => e.scheduledDate)
            .filter(Boolean)
            .map((iso) => new Date(iso))
            .filter((d) => d.getFullYear() === year && d.getMonth() === month)
            .map((d) => d.getDate())
    );

    const selectedDay =
        selectedDate &&
        selectedDate.getFullYear() === year &&
        selectedDate.getMonth() === month
            ? selectedDate.getDate()
            : null;

    const cells = buildCells(year, month, eventDayNumbers, selectedDay);

    function goToPrevMonth() {
        setViewDate(new Date(year, month - 1, 1));
    }

    function goToNextMonth() {
        setViewDate(new Date(year, month + 1, 1));
    }

    function handleDayClick(cell) {
        if (cell.other || !onDayClick) return;
        onDayClick(new Date(year, month, cell.d));
    }

    return (
        <div>
            <div className="cal-bar">
                <button
                    className="btn btn-ghost btn-sm"
                    style={{ border: '1px solid var(--c-border)' }}
                    onClick={goToPrevMonth}
                    aria-label="Mes anterior"
                >
                    <Icon name="chevronLeft" size={14} />
                </button>
                <div style={{ fontSize: '15px', fontWeight: 700 }}>
                    {MONTH_NAMES[month]} {year}
                </div>
                <button
                    className="btn btn-ghost btn-sm"
                    style={{ border: '1px solid var(--c-border)' }}
                    onClick={goToNextMonth}
                    aria-label="Mes siguiente"
                >
                    <Icon name="chevronRight" size={14} />
                </button>
            </div>
            <div className="cal-grid">
                {WEEK.map((w) => (
                    <div className="cal-hd" key={w}>{w}</div>
                ))}
                {cells.map((c) => (
                    <div
                        key={c.key}
                        className={`cal-day ${c.today ? 'today' : ''} ${c.selected ? 'selected' : ''} ${c.event ? 'has-event' : ''} ${c.other ? 'other-month' : 'clickable'}`}
                        onClick={() => handleDayClick(c)}
                    >
                        {c.d}
                    </div>
                ))}
            </div>
        </div>
    );
}
