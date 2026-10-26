import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, ToastContainer, useToast, OFFERER_NAV, profileApi, serviceApi, availabilityApi, isAuthenticated } from '../../../../shared';

import './AvailabilityPage.css';

const WEEKDAYS = [
    { weekDay: 0, label: 'Domingo' },
    { weekDay: 1, label: 'Lunes' },
    { weekDay: 2, label: 'Martes' },
    { weekDay: 3, label: 'Miércoles' },
    { weekDay: 4, label: 'Jueves' },
    { weekDay: 5, label: 'Viernes' },
    { weekDay: 6, label: 'Sábado' },
];

const EMPTY_SCHEDULE = Object.fromEntries(WEEKDAYS.map((d) => [d.weekDay, []]));

function toInputTime(localTime) {
    return localTime ? localTime.slice(0, 5) : '';
}

function toBackendTime(inputTime) {
    return inputTime && inputTime.length === 5 ? `${inputTime}:00` : inputTime;
}

let nextTempId = -1;

function GeneralDayRow({ weekDay, label, slots, onAddSlot, onRemoveSlot, onChangeSlot }) {
    return (
        <div className="day-row">
            <div className="day-inner">
                <span className="day-name">{label}</span>
                <div className="slot-rows">
                    {slots.length === 0 && <span className="slot-empty-day">Sin disponibilidad</span>}
                    {slots.map((slot) => (
                        <div className="slot-range-row" key={slot.id}>
                            <input
                                type="time"
                                className="slot-time-input"
                                value={slot.startTime}
                                onChange={(e) => onChangeSlot(weekDay, slot.id, 'startTime', e.target.value)}
                            />
                            <span className="slot-range-sep">a</span>
                            <input
                                type="time"
                                className="slot-time-input"
                                value={slot.endTime}
                                onChange={(e) => onChangeSlot(weekDay, slot.id, 'endTime', e.target.value)}
                            />
                            <button
                                type="button"
                                className="slot-remove-btn"
                                onClick={() => onRemoveSlot(weekDay, slot.id)}
                                aria-label="Eliminar franja"
                            >
                                <Icon name="x" size={13} />
                            </button>
                        </div>
                    ))}
                    <button type="button" className="slot-add-btn" onClick={() => onAddSlot(weekDay)}>
                        <Icon name="plus" size={13} />
                        Agregar franja
                    </button>
                </div>
            </div>
        </div>
    );
}

function normalizeSchedule(data = []) {
    const grouped = { ...EMPTY_SCHEDULE };
    data.forEach((slot) => {
        const weekDay = Number(slot.weekDay);
        grouped[weekDay] = [
            ...(grouped[weekDay] || []),
            {
                id: slot.id,
                startTime: toInputTime(slot.startTime),
                endTime: toInputTime(slot.endTime),
                active: slot.activeStatus ?? slot.active ?? true,
            },
        ];
    });
    return grouped;
}

function checkOverlappingSlots(slots) {
    // Group slots by weekDay and check for overlaps within each day
    const slotsByDay = {};
    slots.forEach((slot) => {
        const day = slot.weekDay;
        if (!slotsByDay[day]) slotsByDay[day] = [];
        slotsByDay[day].push(slot);
    });

    for (const day in slotsByDay) {
        const daySlots = slotsByDay[day];
        for (let i = 0; i < daySlots.length; i++) {
            for (let j = i + 1; j < daySlots.length; j++) {
                const slot1 = daySlots[i];
                const slot2 = daySlots[j];
                // Check if slot1 and slot2 overlap
                // They overlap if: start1 < end2 AND start2 < end1
                if (slot1.startTime < slot2.endTime && slot2.startTime < slot1.endTime) {
                    return `Las franjas de ${WEEKDAYS[day].label} se cruzan: ${slot1.startTime}-${slot1.endTime} y ${slot2.startTime}-${slot2.endTime}`;
                }
            }
        }
    }
    return null;
}

export function AvailabilityPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [tab, setTab] = useState(0);
    const [schedule, setSchedule] = useState(EMPTY_SCHEDULE);
    const [loading, setLoading] = useState(true);
    const [services, setServices] = useState([]);
    const [selectedServiceId, setSelectedServiceId] = useState('');
    const [serviceSchedule, setServiceSchedule] = useState(EMPTY_SCHEDULE);
    const [serviceLoading, setServiceLoading] = useState(false);
    const [initialServiceSlots, setInitialServiceSlots] = useState([]);

    useEffect(() => {
        if (!isAuthenticated()) {
            navigate('/login');
            return;
        }

        profileApi.getMyProfile()
            .then((profile) => {
                if (!profile?.id) return;
                return serviceApi.getMyServices(profile.id).then((data) => {
                    setServices(data ?? []);
                    if (data?.length) {
                        setSelectedServiceId(String(data[0].id));
                    }
                }).catch(() => setServices([]));
            })
            .catch(() => showToast('No se pudo cargar tu perfil o tus servicios', 'danger'));
    }, [navigate, showToast]);

    useEffect(() => {
        availabilityApi.getMyAvailability()
            .then((data) => {
                const grouped = { ...EMPTY_SCHEDULE };
                for (const slot of data) {
                    if (slot.active === false) continue;
                    grouped[slot.weekDay].push({
                        id: slot.id,
                        startTime: toInputTime(slot.startTime),
                        endTime: toInputTime(slot.endTime),
                    });
                }
                setSchedule(grouped);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!selectedServiceId) return;
        setServiceLoading(true);
        serviceApi.getServiceAvailability(selectedServiceId)
            .then((data) => {
                const normalized = normalizeSchedule(data);
                setServiceSchedule(normalized);
                setInitialServiceSlots((data || []).map((slot) => ({
                    ...slot,
                    active: slot.active ?? slot.activeStatus ?? true,
                })));
            })
            .catch(() => showToast('No se pudo cargar la disponibilidad del servicio', 'danger'))
            .finally(() => setServiceLoading(false));
    }, [selectedServiceId, showToast]);

    const selectedServiceLabel = useMemo(
        () => services.find((service) => String(service.id) === String(selectedServiceId))?.title || 'Selecciona un servicio',
        [services, selectedServiceId]
    );

    function handleAddSlot(weekDay) {
        setSchedule((prev) => ({
            ...prev,
            [weekDay]: [...prev[weekDay], { id: nextTempId--, startTime: '08:00', endTime: '10:00' }],
        }));
    }

    function handleRemoveSlot(weekDay, slotId) {
        setSchedule((prev) => ({
            ...prev,
            [weekDay]: prev[weekDay].filter((s) => s.id !== slotId),
        }));
    }

    function handleChangeSlot(weekDay, slotId, field, value) {
        setSchedule((prev) => ({
            ...prev,
            [weekDay]: prev[weekDay].map((s) =>
                s.id === slotId ? { ...s, [field]: value } : s
            ),
        }));
    }

    function handleAddServiceSlot(weekDay) {
        setServiceSchedule((prev) => ({
            ...prev,
            [weekDay]: [...prev[weekDay], { id: nextTempId--, startTime: '08:00', endTime: '10:00', active: true }],
        }));
    }

    function handleRemoveServiceSlot(weekDay, slotId) {
        setServiceSchedule((prev) => ({
            ...prev,
            [weekDay]: prev[weekDay].filter((s) => s.id !== slotId),
        }));
    }

    function handleChangeServiceSlot(weekDay, slotId, field, value) {
        setServiceSchedule((prev) => ({
            ...prev,
            [weekDay]: prev[weekDay].map((s) =>
                s.id === slotId ? { ...s, [field]: value } : s
            ),
        }));
    }

    function handleSaveGeneralSchedule() {
        const allSlots = Object.entries(schedule).flatMap(([weekDay, slots]) =>
            slots.map((s) => ({
                weekDay: Number(weekDay),
                startTime: toBackendTime(s.startTime),
                endTime: toBackendTime(s.endTime),
                active: true,
            }))
        );

        const invalid = allSlots.some((s) => !s.startTime || !s.endTime || s.startTime >= s.endTime);
        if (invalid) {
            showToast('Revisa que cada franja tenga hora de inicio antes que de fin', 'error');
            return;
        }

        const overlapError = checkOverlappingSlots(allSlots);
        if (overlapError) {
            showToast(overlapError, 'error');
            return;
        }

        availabilityApi.saveMyAvailability(allSlots)
            .then(() => {
                showToast('Horario general guardado', 'success');
            })
            .catch(() => showToast('No se pudo guardar el horario', 'error'));
    }

    async function handleSaveServiceSchedule() {
        if (!selectedServiceId) {
            showToast('Selecciona un servicio para guardar su disponibilidad', 'error');
            return;
        }

        const allSlots = Object.entries(serviceSchedule).flatMap(([weekDay, slots]) =>
            slots.map((s) => ({
                weekDay: Number(weekDay),
                startTime: toBackendTime(s.startTime),
                endTime: toBackendTime(s.endTime),
                isActive: true,
            }))
        );

        const invalid = allSlots.some((s) => !s.startTime || !s.endTime || s.startTime >= s.endTime);
        if (invalid) {
            showToast('Revisa que cada franja tenga hora de inicio antes que de fin', 'error');
            return;
        }

        const overlapError = checkOverlappingSlots(allSlots);
        if (overlapError) {
            showToast(overlapError, 'error');
            return;
        }

        try {
            // Delete only existing slots with IDs
            const toDelete = initialServiceSlots.filter((slot) => slot.id > 0);
            await Promise.all(toDelete.map((slot) => serviceApi.deleteServiceAvailability(slot.id)));
            
            // Create all new slots
            await Promise.all(allSlots.map((slot) => serviceApi.createServiceAvailability(selectedServiceId, slot)));
            
            // Refresh the list
            const refreshed = await serviceApi.getServiceAvailability(selectedServiceId);
            setServiceSchedule(normalizeSchedule(refreshed));
            setInitialServiceSlots((refreshed || []).map((slot) => ({
                ...slot,
                active: slot.activeStatus ?? slot.active ?? true,
            })));
            showToast('Disponibilidad del servicio guardada', 'success');
        } catch (err) {
            console.error('Error saving service availability:', err);
            showToast('No se pudo guardar la disponibilidad del servicio: ' + err.message, 'error');
        }
    }

    async function handleApplyTemplate() {
        if (!selectedServiceId) {
            showToast('Selecciona un servicio para aplicar la plantilla', 'error');
            return;
        }

        try {
            await serviceApi.applyGeneralTemplateToService(selectedServiceId);
            const refreshed = await serviceApi.getServiceAvailability(selectedServiceId);
            const normalized = normalizeSchedule(refreshed);
            setServiceSchedule(normalized);
            setInitialServiceSlots((refreshed || []).map((slot) => ({
                ...slot,
                active: slot.activeStatus ?? slot.active ?? true,
            })));
            showToast('Plantilla general aplicada al servicio', 'success');
        } catch (err) {
            console.error('Error applying template:', err);
            showToast('No se pudo aplicar la plantilla general: ' + err.message, 'error');
        }
    }

    return (
        <DashboardLayout sections={OFFERER_NAV} avatar="CM">
            <div className="ph"><h1>Gestión de disponibilidad</h1><p>Configura tu horario general y por servicio</p></div>

            <div className="tabs">
                <div className={`tab ${tab === 0 ? 'active' : ''}`} onClick={() => setTab(0)}>Horario general</div>
                <div className={`tab ${tab === 1 ? 'active' : ''}`} onClick={() => setTab(1)}>Por servicio</div>
            </div>

            {tab === 0 && (
                <div>
                    <div className="avail-banner">
                        <strong>Plantilla semanal general</strong> — Define los días y horarios en que normalmente estás disponible. Puedes usarla como punto de partida al configurar la disponibilidad de cada servicio.
                    </div>
                    {loading && <p style={{ fontSize: '13px', color: 'var(--c-mid)' }}>Cargando horario...</p>}
                    {!loading && WEEKDAYS.map((d) => (
                        <GeneralDayRow
                            key={d.weekDay}
                            weekDay={d.weekDay}
                            label={d.label}
                            slots={schedule[d.weekDay]}
                            onAddSlot={handleAddSlot}
                            onRemoveSlot={handleRemoveSlot}
                            onChangeSlot={handleChangeSlot}
                        />
                    ))}
                    <div style={{ marginTop: '16px', display: 'flex', gap: '8px' }}>
                        <button className="btn btn-primary" onClick={handleSaveGeneralSchedule}>
                            <Icon name="save" size={15} />
                            Guardar horario general
                        </button>
                    </div>
                </div>
            )}

            {tab === 1 && (
                <div>
                    <div className="input-group" style={{ marginBottom: '16px' }}>
                        <label className="label">Servicio a configurar</label>
                        <select
                            className="input"
                            value={selectedServiceId}
                            onChange={(e) => setSelectedServiceId(e.target.value)}
                        >
                            {services.length === 0 && <option value="">Sin servicios disponibles</option>}
                            {services.map((service) => (
                                <option key={service.id} value={service.id}>{service.title}</option>
                            ))}
                        </select>
                    </div>
                    {selectedServiceLabel && <p style={{ marginBottom: '16px', color: 'var(--c-mid)' }}>Editando: {selectedServiceLabel}</p>}
                    <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                        <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={handleApplyTemplate}>
                            <Icon name="reschedule" size={13} />Aplicar plantilla general
                        </button>
                    </div>
                    {serviceLoading && <p style={{ fontSize: '13px', color: 'var(--c-mid)' }}>Cargando disponibilidad del servicio...</p>}
                    {!serviceLoading && WEEKDAYS.map((d) => (
                        <GeneralDayRow
                            key={d.weekDay}
                            weekDay={d.weekDay}
                            label={d.label}
                            slots={serviceSchedule[d.weekDay] || []}
                            onAddSlot={handleAddServiceSlot}
                            onRemoveSlot={handleRemoveServiceSlot}
                            onChangeSlot={handleChangeServiceSlot}
                        />
                    ))}
                    <div style={{ marginTop: '16px' }}>
                        <button className="btn btn-primary" onClick={handleSaveServiceSchedule}>
                            <Icon name="save" size={15} />Guardar disponibilidad
                        </button>
                    </div>
                </div>
            )}
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default AvailabilityPage;
