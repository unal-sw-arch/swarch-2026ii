import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Stars, ServiceImage, OFFERER_NAV, serviceApi, getApiImageUrl } from '../../../../shared';

const WEEKDAYS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const money = (v) => (v == null ? '—' : '$' + Number(v).toLocaleString('es-CO'));

/**
 * Detalle de un servicio visto por el OFERENTE (su dueño). Similar a la página pública pero SIN
 * el formulario de reserva ni la tarjeta del proveedor (él es el proveedor). Reusa GET /services/{id}/detail.
 */
export function OffererServiceDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const [service, setService] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [activePhoto, setActivePhoto] = useState(0);

    useEffect(() => {
        serviceApi.getServiceDetail(id)
            .then(setService)
            .catch((e) => setError(e.message || 'No se pudo cargar el servicio'))
            .finally(() => setLoading(false));
    }, [id]);

    const shell = (children) => (
        <DashboardLayout sections={OFFERER_NAV}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '18px' }}>
                <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate('/offerer/services')}>
                    <Icon name="chevronLeft" size={14} />Mis servicios
                </button>
            </div>
            {children}
        </DashboardLayout>
    );

    if (loading) return shell(<div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-soft)' }}>Cargando servicio...</div>);
    if (error || !service) {
        return shell(
            <div className="card" style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--c-mid)' }}>
                <Icon name="alertTriangle" size={40} style={{ color: 'var(--c-danger)', marginBottom: '10px' }} />
                <p>{error || 'El servicio no existe.'}</p>
            </div>
        );
    }

    const photos = service.photos || [];
    const rating = service.serviceAverageRating != null ? Number(service.serviceAverageRating) : 0;
    const feedbacks = service.feedbacks || [];

    return shell(
        <>
            {/* Cabecera */}
            <div className="card" style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
                <div style={{ flex: 1, minWidth: '200px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                        <h1 style={{ fontSize: '20px', fontWeight: 800, margin: 0 }}>{service.title}</h1>
                        <span className={`badge ${service.active ? 'badge-success' : 'badge-danger'}`}>{service.active ? 'Activo' : 'Inactivo'}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px', flexWrap: 'wrap' }}>
                        {service.category?.name && <span style={{ fontSize: '13px', color: 'var(--c-mid)' }}>{service.category.name}</span>}
                        <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                            <Stars rating={rating} size={14} /><strong>{rating.toFixed(1)}</strong>
                            <span style={{ fontSize: '12px', color: 'var(--c-soft)' }}>· {service.serviceTotalRatings ?? 0} calif.</span>
                        </span>
                    </div>
                </div>
                <button className="btn btn-outline btn-sm" onClick={() => navigate('/offerer/services')}>
                    <Icon name="edit" size={14} />Editar en Mis servicios
                </button>
            </div>

            {/* Fila superior: galería + panel de datos (balanceado) */}
            <div className="g2" style={{ gap: '20px', alignItems: 'start', marginBottom: '16px' }}>
                {/* Galería */}
                <div className="card">
                    <div style={{ width: '100%', height: '300px', borderRadius: 'var(--r-lg)', overflow: 'hidden', background: 'var(--c-bg-s)' }}>
                        <ServiceImage src={getApiImageUrl(photos[activePhoto])} alt={service.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    </div>
                    {photos.length > 1 && (
                        <div style={{ display: 'flex', gap: '8px', marginTop: '10px', flexWrap: 'wrap' }}>
                            {photos.map((p, i) => (
                                <div key={i} onClick={() => setActivePhoto(i)} style={{ width: '64px', height: '64px', borderRadius: '8px', overflow: 'hidden', cursor: 'pointer', border: i === activePhoto ? '2px solid var(--c-primary)' : '1px solid var(--c-border)' }}>
                                    <ServiceImage src={getApiImageUrl(p)} alt={`${service.title}-${i + 1}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Datos del servicio */}
                <div className="card">
                    <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Datos del servicio</div>
                    <Row label="Precio / hora" value={money(service.priceHourly)} strong />
                    <Row label="Duración media" value={service.averageDurationMinutes != null ? `${service.averageDurationMinutes} min` : '—'} />
                    <Row label="Radio de operación" value={service.operationRadiusKm != null ? `${service.operationRadiusKm} km` : '—'} />
                    <Row label="Servicios completados" value={service.totalCompletedServices ?? 0} />
                    <Row label="Servicios cancelados" value={service.totalCancelledServices ?? 0} />
                    <Row label="Reseñas" value={service.serviceTotalComments ?? feedbacks.length} />
                </div>
            </div>

            {/* Descripción */}
            {service.description && (
                <div className="card" style={{ marginBottom: '16px' }}>
                    <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Descripción</div>
                    <div style={{ fontSize: '13px', color: 'var(--c-mid)', lineHeight: 1.6 }}>{service.description}</div>
                </div>
            )}

            {/* Disponibilidad */}
            <div className="card" style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Disponibilidad</div>
                {service.availability && service.availability.length > 0 ? (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                        {service.availability.map((a, i) => (
                            <div key={a.id || i} style={{ padding: '8px 12px', borderRadius: '8px', background: 'var(--c-bg-s)', fontSize: '12px', minWidth: '110px' }}>
                                <div style={{ fontWeight: 700 }}>{WEEKDAYS[a.weekDay] || `Día ${a.weekDay}`}</div>
                                <div style={{ color: 'var(--c-mid)' }}>{String(a.startTime).substring(0, 5)}–{String(a.endTime).substring(0, 5)}</div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div style={{ fontSize: '13px', color: 'var(--c-soft)' }}>No has configurado horarios de disponibilidad.</div>
                )}
            </div>

            {/* Reseñas */}
            <div className="card">
                <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Reseñas ({feedbacks.length})</div>
                {feedbacks.length > 0 ? feedbacks.map((r, i) => (
                    <div key={i} style={{ paddingBottom: '12px', marginBottom: '12px', borderBottom: i < feedbacks.length - 1 ? '1px solid var(--c-border-s)' : 'none' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '9px', marginBottom: '6px' }}>
                            <div className="av av-sm">{(r.userName || 'US').substring(0, 2).toUpperCase()}</div>
                            <strong style={{ fontSize: '13px' }}>{r.userName || 'Usuario'}</strong>
                            <Stars rating={r.rating ?? 0} size={11} />
                            <span style={{ fontSize: '11px', color: 'var(--c-soft)', marginLeft: 'auto' }}>
                                {r.createdAt ? new Date(r.createdAt).toLocaleDateString('es-CO') : ''}
                            </span>
                        </div>
                        {r.comment && <div style={{ fontSize: '13px', color: 'var(--c-mid)', lineHeight: 1.6 }}>{r.comment}</div>}
                    </div>
                )) : (
                    <div style={{ fontSize: '13px', color: 'var(--c-soft)' }}>Este servicio aún no tiene reseñas.</div>
                )}
            </div>
        </>
    );
}

function Row({ label, value, strong }) {
    return (
        <div style={{ display: 'flex', justifyContent: 'space-between', padding: '7px 0', borderBottom: '1px solid var(--c-border-s)', fontSize: '13px' }}>
            <span style={{ color: 'var(--c-soft)' }}>{label}</span>
            <span style={{ fontWeight: strong ? 700 : 600, color: strong ? 'var(--c-primary-d)' : 'var(--c-text)' }}>{value}</span>
        </div>
    );
}

export default OffererServiceDetailPage;
