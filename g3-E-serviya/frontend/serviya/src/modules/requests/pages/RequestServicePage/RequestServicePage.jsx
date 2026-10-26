import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AppNavbar, Icon, Modal, ToastContainer, useToast } from '../../../../shared';
import { requestApi, serviceApi, addressApi, profileApi } from '../../../../shared/api';

import './RequestServicePage.css';

const STEPS = ['Servicio', 'Fecha/Hora', 'Dirección', 'Confirmar'];

function StepBar({ step }) {
    return (
        <div className="step-bar">
            {STEPS.map((label, i) => {
                const n = i + 1;
                const cls = n < step ? 'step-done' : n === step ? 'step-active' : 'step-pending';
                return (
                    <React.Fragment key={label}>
                        {i > 0 && <div className={`step-line ${n <= step ? 'done' : ''}`} />}
                        <div className="step-item">
                            <div className={`step-circle ${cls}`}>{n < step ? <Icon name="check" size={15} strokeWidth={2.5} /> : n}</div>
                            <div className={`step-label ${n === step ? 'active' : ''}`}>{label}</div>
                        </div>
                    </React.Fragment>
                );
            })}
        </div>
    );
}

export function RequestServicePage() {
    const navigate = useNavigate();
    const { serviceId } = useParams();
    const { toasts, showToast } = useToast();

    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(true);
    const [service, setService] = useState(null);
    const [availability, setAvailability] = useState([]);
    const [addresses, setAddresses] = useState([]);
    const [selectedDate, setSelectedDate] = useState('');
    const [selectedSlot, setSelectedSlot] = useState(null);
    const [selectedAddressId, setSelectedAddressId] = useState('');
    const [newAddress, setNewAddress] = useState({ addressLine: '', city: '', reference: '' });
    const [showNewAddressForm, setShowNewAddressForm] = useState(false);
    const [creating, setCreating] = useState(false);
    const [successOpen, setSuccessOpen] = useState(false);
    const [createdRequestId, setCreatedRequestId] = useState(null);

    // Load service details on mount
    useEffect(() => {
        if (!serviceId) {
            showToast('ID de servicio no proporcionado', 'danger');
            navigate('/services');
            return;
        }

        const loadService = async () => {
            try {
                const svc = await serviceApi.getServiceDetail(serviceId);
                setService(svc);
            } catch (err) {
                showToast(err.message || 'No se pudo cargar el servicio', 'danger');
                navigate('/services');
            }
        };

        loadService();
    }, [serviceId, navigate, showToast]);

    // Load availability when service loads
    useEffect(() => {
        if (service?.id) {
            const loadAvailability = async () => {
                try {
                    const data = await serviceApi.getServiceAvailability(service.id);
                    setAvailability(data.content ?? []);
                } catch {
                    // Ignore - availability is optional
                }
            };
            loadAvailability();
        }
    }, [service?.id]);

    // Load user addresses
    useEffect(() => {
        const loadAddresses = async () => {
            try {
                const data = await addressApi.getMyAddresses();
                const addrList = Array.isArray(data) ? data : data.content ?? [];
                setAddresses(addrList);
                if (addrList.length > 0 && !selectedAddressId) {
                    setSelectedAddressId(addrList[0].id);
                }
            } catch {
                // Ignore - user may not have addresses
            }
        };
        loadAddresses();
    }, [selectedAddressId]);

    const handleCreateRequest = useCallback(async () => {
        if (!service || !selectedDate || !selectedSlot || !selectedAddressId) {
            showToast('Complete todos los campos requeridos', 'warning');
            return;
        }

        setCreating(true);
        try {
            const payload = {
                serviceId: service.id,
                scheduledAt: `${selectedDate}T${selectedSlot}:00`,
                addressId: Number(selectedAddressId),
            };
            const result = await requestApi.createRequest(payload);
            setCreatedRequestId(result.id);
            showToast('Solicitud creada correctamente', 'success');
            setSuccessOpen(true);
        } catch (err) {
            showToast(err.message || 'No se pudo crear la solicitud', 'danger');
        } finally {
            setCreating(false);
        }
    }, [service, selectedDate, selectedSlot, selectedAddressId, showToast]);

    if (loading || !service) {
        return (
            <>
                <AppNavbar avatar="JP" showBell={false} />
                <div className="req-wrap">
                    <div className="req-card-wizard">
                        <StepBar step={1} />
                        <div style={{ textAlign: 'center', padding: '40px' }}>
                            <div className="spinner" style={{ margin: '0 auto 16px' }} />
                            <div>Cargando servicio...</div>
                        </div>
                    </div>
                </div>
            </>
        );
    }

    const availableSlots = availability
        .filter((s) => s.isActive)
        .map((s) => ({ start: s.startTime, end: s.endTime }));

    const formattedDate = selectedDate ? new Date(selectedDate + 'T00:00:00').toLocaleDateString('es-CO', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    }) : '';

    const selectedAddress = addresses.find((a) => a.id === Number(selectedAddressId));

    return (
        <>
            <AppNavbar avatar="JP" showBell={false} />

            <div className="req-wrap">
                <div className="req-card-wizard">
                    <StepBar step={step} />

                    <div className="service-summary">
                        <div className="service-summary-ico"><Icon name="wrench" size={20} /></div>
                        <div>
                            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--c-text)' }}>{service.title}</div>
                            <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '2px' }}>
                                {service.offererName ?? 'Oferente'} · {service.categoryName ?? 'Servicio'}
                                {service.priceHourly != null && ` · desde $${service.priceHourly.toLocaleString('es-CO')}`}
                            </div>
                        </div>
                    </div>

                    {/* Step 1: Service Selection (already done via URL) */}
                    {step === 1 && (
                        <div>
                            <div className="wiz-title">Confirmar servicio</div>
                            <div className="wiz-sub">¿Deseas solicitar este servicio?</div>
                            <div style={{ marginBottom: '16px' }}>
                                {service.description && (
                                    <div style={{ fontSize: '12px', color: 'var(--c-mid)', lineHeight: 1.4, marginBottom: '12px' }}>
                                        {service.description}
                                    </div>
                                )}
                                <div style={{ display: 'flex', gap: '14px', flexWrap: 'wrap', fontSize: '13px' }}>
                                    {service.priceHourly != null && (
                                        <div>
                                            <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Precio/hora </span>
                                            <span style={{ fontWeight: 600 }}>$${service.priceHourly.toLocaleString('es-CO')}</span>
                                        </div>
                                    )}
                                    {service.averageDurationMinutes != null && (
                                        <div>
                                            <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Duración </span>
                                            <span>{service.averageDurationMinutes} min</span>
                                        </div>
                                    )}
                                    {service.operationRadiusKm != null && (
                                        <div>
                                            <span style={{ color: 'var(--c-soft)', fontSize: '11px', textTransform: 'uppercase' }}>Radio </span>
                                            <span>{service.operationRadiusKm} km</span>
                                        </div>
                                    )}
                                </div>
                            </div>
                            <div className="wiz-actions">
                                <button className="btn btn-ghost btn-full" onClick={() => navigate('/services')}>
                                    <Icon name="chevronLeft" size={15} />Volver a servicios
                                </button>
                                <button className="btn btn-primary btn-full btn-lg" onClick={() => setStep(2)}>
                                    Continuar<Icon name="chevronRight" size={17} />
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Step 2: Date/Time Selection */}
                    {step === 2 && (
                        <div>
                            <div className="wiz-title">Elige fecha y hora</div>
                            <div className="wiz-sub">Selecciona según la disponibilidad del oferente</div>

                            <div className="input-group">
                                <label className="label">Fecha</label>
                                <input
                                    className="input"
                                    type="date"
                                    min={new Date().toISOString().split('T')[0]}
                                    value={selectedDate}
                                    onChange={(e) => setSelectedDate(e.target.value)}
                                    required
                                />
                            </div>

                            <div className="input-group">
                                <label className="label">Horario disponible</label>
                                {availableSlots.length === 0 ? (
                                    <div style={{ color: 'var(--c-mid)', fontSize: '13px', padding: '16px', textAlign: 'center' }}>
                                        No hay horarios disponibles configurados para este servicio.
                                    </div>
                                ) : (
                                    <div className="time-slots">
                                        {availableSlots.map((s) => (
                                            <div
                                                key={`${s.start}-${s.end}`}
                                                className={`time-slot ${selectedSlot === s.start ? 'active' : ''}`}
                                                onClick={() => setSelectedSlot(s.start)}
                                            >
                                                {s.start} - {s.end}
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div className="wiz-actions">
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(1)}>
                                    <Icon name="chevronLeft" size={15} />Atrás
                                </button>
                                <button
                                    className="btn btn-primary btn-full btn-lg"
                                    onClick={() => setStep(3)}
                                    disabled={!selectedDate || !selectedSlot}
                                >
                                    Continuar<Icon name="chevronRight" size={17} />
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Step 3: Address Selection */}
                    {step === 3 && (
                        <div>
                            <div className="wiz-title">Dirección del servicio</div>
                            <div className="wiz-sub">¿Dónde necesitas el servicio?</div>

                            {addresses.length > 0 && (
                                <div className="input-group" style={{ marginBottom: '12px' }}>
                                    <label className="label">Selecciona una dirección guardada</label>
                                    <select
                                        className="input"
                                        value={selectedAddressId}
                                        onChange={(e) => {
                                            setSelectedAddressId(e.target.value);
                                            setShowNewAddressForm(false);
                                        }}
                                    >
                                        {addresses.map((addr) => (
                                            <option key={addr.id} value={addr.id}>
                                                {addr.addressLine}, {addr.city} {addr.isMain ? '(Principal)' : ''}
                                            </option>
                                        ))}
                                        <option value="new">+ Ingresar nueva dirección</option>
                                    </select>
                                </div>
                            )}

                            {showNewAddressForm || addresses.length === 0 ? (
                                <div className="input-group" style={{ marginBottom: '12px' }}>
                                    <label className="label">Nueva dirección</label>
                                    <input
                                        className="input"
                                        type="text"
                                        placeholder="Calle, número..."
                                        value={newAddress.addressLine}
                                        onChange={(e) => setNewAddress({ ...newAddress, addressLine: e.target.value })}
                                    />
                                    <input
                                        className="input"
                                        type="text"
                                        placeholder="Ciudad"
                                        value={newAddress.city}
                                        onChange={(e) => setNewAddress({ ...newAddress, city: e.target.value })}
                                        style={{ marginTop: '8px' }}
                                    />
                                    <input
                                        className="input"
                                        type="text"
                                        placeholder="Referencia (opcional)"
                                        value={newAddress.reference}
                                        onChange={(e) => setNewAddress({ ...newAddress, reference: e.target.value })}
                                        style={{ marginTop: '8px' }}
                                    />
                                    <button
                                        className="btn btn-secondary btn-full"
                                        style={{ marginTop: '8px' }}
                                        onClick={async () => {
                                            if (!newAddress.addressLine || !newAddress.city) {
                                                showToast('Complete dirección y ciudad', 'warning');
                                                return;
                                            }
                                            try {
                                                const created = await addressApi.createAddress({
                                                    addressLine: newAddress.addressLine,
                                                    city: newAddress.city,
                                                    reference: newAddress.reference,
                                                });
                                                setAddresses((prev) => [...prev, created]);
                                                setSelectedAddressId(created.id);
                                                setShowNewAddressForm(false);
                                                setNewAddress({ addressLine: '', city: '', reference: '' });
                                                showToast('Dirección agregada', 'success');
                                            } catch (err) {
                                                showToast(err.message || 'No se pudo crear la dirección', 'danger');
                                            }
                                        }}
                                    >
                                        Guardar dirección
                                    </button>
                                </div>
                            ) : (
                                <button
                                    className="btn btn-link"
                                    style={{ marginBottom: '12px' }}
                                    onClick={() => setShowNewAddressForm(true)}
                                >
                                    + Ingresar nueva dirección
                                </button>
                            )}

                            {selectedAddress && (
                                <div className="map-placeholder">
                                    <Icon name="mapPin" size={20} />&nbsp;
                                    {selectedAddress.addressLine}, {selectedAddress.city}
                                    {selectedAddress.reference && ` - ${selectedAddress.reference}`}
                                </div>
                            )}

                            <div className="input-group">
                                <label className="label">Referencia adicional (opcional)</label>
                                <input className="input" placeholder="Piso, apartamento, indicaciones..." />
                            </div>

                            <div className="wiz-actions">
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(2)}>
                                    <Icon name="chevronLeft" size={15} />Atrás
                                </button>
                                <button
                                    className="btn btn-primary btn-full btn-lg"
                                    onClick={() => setStep(4)}
                                    disabled={!selectedAddressId}
                                >
                                    Continuar<Icon name="chevronRight" size={17} />
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Step 4: Confirmation */}
                    {step === 4 && (
                        <div>
                            <div className="wiz-title">Confirma tu solicitud</div>
                            <div className="wiz-sub">Revisa los detalles antes de enviar</div>
                            <div className="confirm-box">
                                <div className="confirm-row"><span className="confirm-label">Servicio</span><span className="confirm-value">{service.title}</span></div>
                                <div className="confirm-row"><span className="confirm-label">Oferente</span><span className="confirm-value">{service.offererName ?? 'N/A'}</span></div>
                                <div className="confirm-row"><span className="confirm-label">Fecha</span><span className="confirm-value">{formattedDate}</span></div>
                                <div className="confirm-row"><span className="confirm-label">Hora</span><span className="confirm-value">{selectedSlot ? `${selectedSlot} - ${availableSlots.find(s => s.start === selectedSlot)?.end ?? ''}` : ''}</span></div>
                                <div className="confirm-row"><span className="confirm-label">Dirección</span><span className="confirm-value">
                                    {selectedAddress ? `${selectedAddress.addressLine}, ${selectedAddress.city}` : 'Nueva dirección'}
                                    {selectedAddress?.reference && ` - ${selectedAddress.reference}`}
                                </span></div>
                                {service.priceHourly != null && (
                                    <div className="confirm-row"><span className="confirm-label">Precio estimado</span><span className="confirm-value" style={{ color: 'var(--c-primary-d)' }}>$${service.priceHourly.toLocaleString('es-CO')}/hora</span></div>
                                )}
                            </div>
                            <div className="confirm-note"><strong>Nota:</strong> El servicio está sujeto a confirmación del oferente. Recibirás una notificación por el canal interno y por correo cuando acepte o rechace tu solicitud.</div>
                            <div className="wiz-actions">
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(3)}>
                                    <Icon name="chevronLeft" size={15} />Atrás
                                </button>
                                <button
                                    className="btn btn-primary btn-full btn-lg"
                                    onClick={handleCreateRequest}
                                    disabled={creating}
                                >
                                    {creating ? 'Enviando...' : <><Icon name="check" size={17} />Enviar solicitud</>}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Success Modal */}
            <Modal open={successOpen} onClose={() => setSuccessOpen(false)}>
                <div style={{ textAlign: 'center' }}>
                    <div className="success-ico"><Icon name="check" size={30} /></div>
                    <div style={{ fontSize: '20px', fontWeight: 800, marginBottom: '8px' }}>¡Solicitud enviada!</div>
                    <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '22px', lineHeight: 1.65 }}>
                        Tu solicitud fue enviada correctamente. Te notificaremos por el sistema interno y por correo cuando el oferente acepte o rechace.
                    </div>
                    <button className="btn btn-primary btn-full" onClick={() => navigate('/requests')}>Ver mis solicitudes</button>
                </div>
            </Modal>

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default RequestServicePage;