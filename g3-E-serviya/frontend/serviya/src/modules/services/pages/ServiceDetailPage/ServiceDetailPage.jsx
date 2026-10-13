import { useState, useEffect } from "react";
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AppNavbar, Avatar, ServiceImage, Icon, Modal, Stars, WhatsAppButton, ToastContainer, useToast, reportApi, serviceApi, addressApi, requestApi, getApiImageUrl } from '../../../../shared';

import './ServiceDetailPage.css';

const DAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

const TIME_OPTIONS = [
    { label: '9:00 AM',  value: '09:00:00' },
    { label: '10:00 AM', value: '10:00:00' },
    { label: '11:00 AM', value: '11:00:00' },
    { label: '2:00 PM',  value: '14:00:00' },
    { label: '3:00 PM',  value: '15:00:00' },
    { label: '4:00 PM',  value: '16:00:00' },
    { label: '5:00 PM',  value: '17:00:00' },
];

const getWeekDayName = (dayNum) => {
    const idx = (dayNum - 1) % 7;
    return DAYS[idx] || 'Día';
};

export function ServiceDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [activeSlot, setActiveSlot] = useState(0);
    const [feedbackReportOpen, setFeedbackReportOpen] = useState(false);
    const [feedbackTarget, setFeedbackTarget] = useState(null);
    const [feedbackReportCategory, setFeedbackReportCategory] = useState('Contenido inapropiado');
    const [feedbackCustomCategory, setFeedbackCustomCategory] = useState('');
    const [feedbackReportReason, setFeedbackReportReason] = useState('');

    const handleOpenFeedbackReport = (review) => {
        setFeedbackTarget(review);
        setFeedbackReportCategory('Contenido inapropiado');
        setFeedbackCustomCategory('');
        setFeedbackReportReason('');
        setFeedbackReportOpen(true);
    };

    const handleSubmitFeedbackReport = async () => {
        if (!feedbackTarget) return;

        try {
            await reportApi.createServiceFeedbackReport({
                reportedUserId: feedbackTarget.userId,
                category: feedbackReportCategory,
                customCategory: feedbackCustomCategory,
                reason: feedbackReportReason,
                serviceFeedbackId: feedbackTarget.id,
            });
            setFeedbackReportOpen(false);
            setFeedbackTarget(null);
            setFeedbackReportCategory('Contenido inapropiado');
            setFeedbackCustomCategory('');
            setFeedbackReportReason('');
            showToast('Reporte de reseña enviado al administrador', 'success');
        } catch (error) {
            showToast(error.message || 'No se pudo enviar el reporte de la reseña', 'danger');
        }
    };

    // Estados para la carga del detalle del servicio
    const [service, setService] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Estados para el formulario de solicitud
    const [date, setDate] = useState('');
    const [selectedTime, setSelectedTime] = useState(TIME_OPTIONS[0].value);
    const [selectedAddressId, setSelectedAddressId] = useState('');
    const [addresses, setAddresses] = useState([]);
    const [submitting, setSubmitting] = useState(false);
    const [successOpen, setSuccessOpen] = useState(false);
    const [activePhotoIndex, setActivePhotoIndex] = useState(0);
    const [previewPhoto, setPreviewPhoto] = useState(null);

    useEffect(() => {
        const loadServiceDetail = async () => {
            setLoading(true);
            try {
                const data = await serviceApi.getServiceDetail(id);
                setService(data);
                setActivePhotoIndex(0);
            } catch (err) {
                setError(err.message);
                showToast("Error al cargar detalle del servicio: " + err.message, "error");
            } finally {
                setLoading(false);
            }
        };

        if (id) {
            loadServiceDetail();
        }
    }, [id]);

    useEffect(() => {
        addressApi.getMyAddresses()
            .then((data) => {
                setAddresses(data || []);
                if (data && data.length > 0) setSelectedAddressId(String(data[0].id));
            })
            .catch(() => {/* usuario no autenticado o sin direcciones, se ignora silenciosamente */});
    }, []);

    const handleRequestService = async () => {
        if (!date) { showToast('Por favor selecciona una fecha', 'error'); return; }
        if (!selectedAddressId) { showToast('Por favor selecciona una dirección', 'error'); return; }

        setSubmitting(true);
        try {
            await requestApi.createRequest({
                serviceId: parseInt(id),
                addressId: parseInt(selectedAddressId),
                scheduledDate: `${date}T${selectedTime}`,
            });
            setSuccessOpen(true);
        } catch (err) {
            showToast('Error al enviar solicitud: ' + err.message, 'error');
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <>
                <AppNavbar links={[{ to: '/services', label: '← Resultados' }]} />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '60vh', color: 'var(--c-soft)' }}>
                    <div className="loading-spinner" style={{ width: '40px', height: '40px', border: '4px solid var(--c-border)', borderTop: '4px solid var(--c-primary)', borderRadius: '50%', animation: 'spin 1s linear infinite', marginBottom: '15px' }} />
                    <span>Cargando detalles del servicio...</span>
                    <style>{`
                        @keyframes spin {
                            0% { transform: rotate(0deg); }
                            100% { transform: rotate(360deg); }
                        }
                    `}</style>
                </div>
                <ToastContainer toasts={toasts} />
            </>
        );
    }

    if (error || !service) {
        return (
            <>
                <AppNavbar links={[{ to: '/services', label: '← Resultados' }]} />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '60vh', color: 'var(--c-soft)', padding: '20px', textAlign: 'center' }}>
                    <Icon name="alertTriangle" size={48} style={{ color: 'var(--c-danger)', marginBottom: '15px' }} />
                    <h3>Error al cargar servicio</h3>
                    <p>{error || "El servicio no existe o no se pudo encontrar."}</p>
                    <Link to="/services" className="btn btn-primary" style={{ marginTop: '15px' }}>Volver al buscador</Link>
                </div>
                <ToastContainer toasts={toasts} />
            </>
        );
    }

    // Datos del servicio
    const rating = service.serviceAverageRating !== null && service.serviceAverageRating !== undefined ? service.serviceAverageRating : 0.0;
    const offererRating = service.offererAverageRating != null ? service.offererAverageRating : 0.0;
    const feedbacksCount = service.feedbacks ? service.feedbacks.length : 0;
    const initials = service.fullName ? service.fullName.substring(0, 2).toUpperCase() : "OF";
    const photos = service.photos || [];
    const activePhoto = photos[activePhotoIndex] || null;
    const getPhotoSrc = (photo) => getApiImageUrl(photo);

    return (
        <>
            <AppNavbar links={[{ to: '/services', label: '← Resultados' }]} />

            <div className="breadcrumb">
                <Link to="/">Inicio</Link> / <Link to="/services">{service.category?.name || "Categoría"}</Link> / {service.title}
            </div>

            <div className="detail-layout">
                <div>
                    <div className="service-hero">
                        {photos.length > 0 ? (
                            <div className="service-gallery">
                                <div className="service-gallery-main" onClick={() => setPreviewPhoto(getPhotoSrc(activePhoto))}>
                                    <ServiceImage src={getPhotoSrc(activePhoto)} alt={`${service.title}-${activePhotoIndex + 1}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                    {photos.length > 1 && (
                                        <>
                                            <button
                                                type="button"
                                                className="gallery-nav gallery-nav-left"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    setActivePhotoIndex((prev) => (prev === 0 ? photos.length - 1 : prev - 1));
                                                }}
                                            >
                                                <Icon name="chevronLeft" size={18} />
                                            </button>
                                            <button
                                                type="button"
                                                className="gallery-nav gallery-nav-right"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    setActivePhotoIndex((prev) => (prev === photos.length - 1 ? 0 : prev + 1));
                                                }}
                                            >
                                                <Icon name="chevronRight" size={18} />
                                            </button>
                                        </>
                                    )}
                                </div>
                                {photos.length > 1 && (
                                    <div className="gallery-thumbs">
                                        {photos.map((photo, index) => (
                                            <button
                                                key={`${photo}-${index}`}
                                                type="button"
                                                className={`gallery-thumb ${index === activePhotoIndex ? 'active' : ''}`}
                                                onClick={() => setActivePhotoIndex(index)}
                                            >
                                                <ServiceImage src={getPhotoSrc(photo)} alt={`${service.title}-${index + 1}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="service-hero-placeholder">
                                <Icon name="wrench" size={64} strokeWidth={1.2} />
                            </div>
                        )}
                        <div className="service-hero-av">
                            <span className={`badge ${service.active ? 'badge-success' : 'badge-warn'}`}>
                                {service.active ? 'Disponible' : 'Inactivo'}
                            </span>
                        </div>
                    </div>
                    <div className="detail-title">{service.title}</div>
                    <div className="detail-meta">
                        <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                            <Stars rating={rating} size={12} />
                            <strong style={{ color: 'var(--c-text)' }}>{rating.toFixed(1)}</strong>
                            ({feedbacksCount} reseñas)
                        </span>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Icon name="mapPin" size={13} />
                            {service.operationRadiusKm ? `${service.operationRadiusKm} km` : 'Sin límite'}
                        </span>
                        {service.averageDurationMinutes && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <Icon name="clock" size={13} />
                                Duración est.: {Math.floor(service.averageDurationMinutes / 60)}h {service.averageDurationMinutes % 60}m
                            </span>
                        )}
                        <span className="badge badge-primary">{service.category?.name || "General"}</span>
                    </div>

                    <div className="sec-card">
                        <div className="oferer-row" onClick={() => navigate(`/offerers/${service.userId}`)}>
                            <div className="av av-lg">
                                <Avatar src={getApiImageUrl(service.profilePhotoUrl)} initials={initials} alt="Foto del oferente" />
                            </div>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--c-text)' }}>{service.fullName || "Oferente"}</div>
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)', margin: '2px 0' }}>{service.specialty || "Especialista en ServiYa"}</div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <Stars rating={offererRating} size={11} />
                                    <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>
                                        {offererRating.toFixed(1)} · {service.totalCompletedServices ?? 0} servicios completados
                                    </span>
                                    <span className="badge badge-success" style={{ fontSize: '10px' }}>Verificado</span>
                                </div>
                            </div>
                            <span style={{ color: 'var(--c-primary)', fontSize: '12px', fontWeight: 600 }}>Ver perfil →</span>
                        </div>
                    </div>

                    <div className="sec-card">
                        <h3>Descripción del servicio</h3>
                        <div style={{ fontSize: '13px', color: 'var(--c-mid)', lineHeight: 1.7 }}>
                            {service.description || "Sin descripción proporcionada."}
                            {service.operationRadiusKm && (
                                <>
                                    <br /><br />
                                    <strong style={{ color: 'var(--c-text)' }}>Radio de operación:</strong> hasta {service.operationRadiusKm} km desde la ubicación principal.
                                </>
                            )}
                        </div>
                    </div>

                    <div className="sec-card">
                        <h3>Disponibilidad</h3>
                        {service.availability && service.availability.length > 0 ? (
                            <div className="avail-grid">
                                {service.availability.map((avail, i) => {
                                    const dayName = getWeekDayName(avail.weekDay);
                                    const start = avail.startTime.substring(0, 5);
                                    const end = avail.endTime.substring(0, 5);
                                    return (
                                        <div
                                            key={avail.id || i}
                                            className={`avail-slot ${activeSlot === i ? 'active' : ''}`}
                                            onClick={() => setActiveSlot(i)}
                                        >
                                            <div className="avail-lbl">{dayName}</div>
                                            <div className="avail-day">Día {avail.weekDay}</div>
                                            <div className="avail-hr">{start}–{end}</div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div style={{ fontSize: '13px', color: 'var(--c-soft)', textAlign: 'center', padding: '10px' }}>
                                No hay horarios de disponibilidad configurados por el oferente.
                            </div>
                        )}
                    </div>

                    <div className="sec-card">
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0 }}>Reseñas ({feedbacksCount})</h3>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <Stars rating={rating} />
                                <strong style={{ fontSize: '18px' }}>{rating.toFixed(1)}</strong>
                            </div>
                        </div>
                        {service.feedbacks && service.feedbacks.length > 0 ? (
                            service.feedbacks.map((r, i) => {
                                const fbInitials = r.userName ? r.userName.substring(0, 2).toUpperCase() : "US";
                                return (
                                    <div className="review-item" key={r.id || i}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '9px', marginBottom: '7px' }}>
                                            <div className="av av-sm">{fbInitials}</div>
                                            <strong style={{ fontSize: '13px' }}>{r.userName || "Usuario"}</strong>
                                            <Stars rating={r.rating ?? 0} size={11} />
                                            <span style={{ fontSize: '11px', color: 'var(--c-soft)', marginLeft: 'auto' }}>
                                                {r.createdAt ? new Date(r.createdAt).toLocaleDateString() : 'Hace poco'}
                                            </span>
                                            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--c-danger)', padding: '4px 8px', marginLeft: '4px' }} onClick={() => handleOpenFeedbackReport(r)}>
                                                <Icon name="alertTriangle" size={13} />Reportar reseña
                                            </button>
                                        </div>
                                        <div style={{ fontSize: '13px', color: 'var(--c-mid)', lineHeight: 1.6 }}>{r.comment}</div>
                                    </div>
                                );
                            })
                        ) : (
                            <div style={{ fontSize: '13px', color: 'var(--c-soft)', textAlign: 'center', padding: '10px' }}>
                                Aún no hay reseñas para este servicio.
                            </div>
                        )}
                    </div>
                </div>

                <div>
                    <div className="booking-card">
                        <div className="booking-price">desde ${service.priceHourly ? service.priceHourly.toLocaleString() : '0'}</div>
                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '16px' }}>/ por hora de servicio</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '18px', fontSize: '12px', color: 'var(--c-mid)' }}>
                            <Stars rating={rating} size={12} /> {rating.toFixed(1)} · {feedbacksCount} reseñas
                        </div>

                        <div className="input-group">
                            <label className="label">Fecha</label>
                            <input
                                className="input"
                                type="date"
                                value={date}
                                min={new Date().toISOString().split('T')[0]}
                                onChange={(e) => setDate(e.target.value)}
                            />
                        </div>
                        <div className="input-group">
                            <label className="label">Hora</label>
                            <select className="input" value={selectedTime} onChange={(e) => setSelectedTime(e.target.value)}>
                                {TIME_OPTIONS.map((opt) => (
                                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="input-group">
                            <label className="label">Dirección</label>
                            {addresses.length > 0 ? (
                                <select
                                    className="input"
                                    value={selectedAddressId}
                                    onChange={(e) => {
                                        if (e.target.value === '__new') navigate('/profile');
                                        else setSelectedAddressId(e.target.value);
                                    }}
                                >
                                    {addresses.map((addr) => (
                                        <option key={addr.id} value={String(addr.id)}>
                                            {addr.addressLine}{addr.city ? `, ${addr.city}` : ''}
                                        </option>
                                    ))}
                                    <option value="__new">+ Agregar dirección</option>
                                </select>
                            ) : (
                                <button
                                    className="btn btn-ghost btn-full"
                                    style={{ fontSize: '12px' }}
                                    onClick={() => navigate('/profile')}
                                >
                                    <Icon name="mapPin" size={14} />Agregar una dirección
                                </button>
                            )}
                        </div>

                        <div className="metrics-mini">
                            <div className="mm">
                                <div className="mm-val">{service.totalCompletedServices != null ? `${Math.round(service.totalCompletedServices / Math.max(service.totalCompletedServices + (service.totalCancelledServices ?? 0), 1) * 100)}%` : 'N/A'}</div>
                                <div className="mm-lbl">Cumplimiento</div>
                            </div>
                            <div className="mm">
                                <div className="mm-val">{service.totalCancelledServices != null ? `${Math.round((service.totalCancelledServices ?? 0) / Math.max(service.totalCompletedServices + (service.totalCancelledServices ?? 0), 1) * 100)}%` : 'N/A'}</div>
                                <div className="mm-lbl">Cancelaciones</div>
                            </div>
                            <div className="mm">
                                <div className="mm-val">{service.totalCompletedServices ?? feedbacksCount}</div>
                                <div className="mm-lbl">Servicios</div>
                            </div>
                            <div className="mm">
                                <div className="mm-val">{service.serviceAverageRating != null ? `${service.serviceAverageRating.toFixed(1)}★` : `${rating.toFixed(1)}★`}</div>
                                <div className="mm-lbl">Calificación</div>
                            </div>
                        </div>

                        <button
                            className="btn btn-primary btn-full btn-lg"
                            onClick={handleRequestService}
                            disabled={submitting}
                            style={{ marginBottom: '8px' }}
                        >
                            {submitting
                                ? <><Icon name="clock" size={17} />Enviando...</>
                                : <><Icon name="calendar" size={17} />Solicitar servicio</>
                            }
                        </button>
                        {service.whatsappNumber && (
                            <WhatsAppButton 
                                block 
                                label="Contactar por WhatsApp" 
                                iconSize={16} 
                                onClick={() => {
                                    showToast('Abriendo WhatsApp...', 'success');
                                    window.open(`https://wa.me/${service.whatsappNumber}`, '_blank');
                                }} 
                            />
                        )}
                        <div className="verified-note">
                            <Icon name="shield" size={13} style={{ color: 'var(--c-success)' }} />Perfil verificado por ServiYa
                        </div>
                    </div>
                </div>
            </div>

            <Modal open={feedbackReportOpen} onClose={() => setFeedbackReportOpen(false)}>
                <div className="modal-title">Reportar reseña</div>
                <div className="modal-sub">{feedbackTarget ? `Estás reportando la reseña de ${feedbackTarget.userName || 'este usuario'}.` : 'Indica el motivo del reporte.'}</div>
                <div className="input-group"><label className="label">Categoría</label><select className="input" value={feedbackReportCategory} onChange={(e) => setFeedbackReportCategory(e.target.value)}><option>Contenido inapropiado</option><option>Spam</option><option>Amenaza o acoso</option><option>Otra</option></select></div>
                {feedbackReportCategory === 'Otra' && <div className="input-group"><label className="label">Categoría personalizada</label><input className="input" value={feedbackCustomCategory} onChange={(e) => setFeedbackCustomCategory(e.target.value)} placeholder="Escribe la categoría" /></div>}
                <div className="input-group"><label className="label">Descripción</label><textarea className="input" value={feedbackReportReason} onChange={(e) => setFeedbackReportReason(e.target.value)} placeholder="Describe lo que ocurrió..." /></div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setFeedbackReportOpen(false)}>Cancelar</button>
                    <button className="btn btn-danger btn-full" onClick={handleSubmitFeedbackReport}>Enviar reporte</button>
                </div>
            </Modal>

            <Modal open={successOpen} onClose={() => setSuccessOpen(false)}>
                <div style={{ textAlign: 'center', padding: '8px 0' }}>
                    <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: 'var(--c-success-bg, #ECFDF5)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px', color: 'var(--c-success, #10B981)' }}>
                        <Icon name="check" size={28} strokeWidth={2.5} />
                    </div>
                    <div style={{ fontSize: '20px', fontWeight: 800, marginBottom: '8px' }}>¡Solicitud enviada!</div>
                    <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '22px', lineHeight: 1.65 }}>
                        Tu solicitud fue enviada a <strong>{service?.fullName || 'el oferente'}</strong>. Te notificaremos cuando acepte o rechace.
                    </div>
                    <button className="btn btn-primary btn-full" onClick={() => navigate('/requests')}>Ver mis solicitudes</button>
                    <button className="btn btn-ghost btn-full" style={{ marginTop: '8px' }} onClick={() => setSuccessOpen(false)}>Seguir explorando</button>
                </div>
            </Modal>

            {previewPhoto && (
                <Modal open={true} onClose={() => setPreviewPhoto(null)} maxWidth={860}>
                    <div className="gallery-preview">
                        <ServiceImage src={previewPhoto} alt="Vista previa de la imagen" style={{ maxWidth: '100%' }} />
                    </div>
                </Modal>
            )}

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default ServiceDetailPage;
