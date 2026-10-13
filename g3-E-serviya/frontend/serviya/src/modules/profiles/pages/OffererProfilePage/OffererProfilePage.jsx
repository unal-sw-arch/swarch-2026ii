import { useState, useEffect } from "react";
import { useNavigate, useParams } from 'react-router-dom';
import { AppNavbar, Avatar, Icon, Stars, WhatsAppButton, ToastContainer, useToast, profileApi, getApiImageUrl } from '../../../../shared';
import { metricsApi } from '../../../../shared/api';

import './OffererProfilePage.css';

const SERVICES = [
    { name: 'Reparación de tuberías', rating: 4.9, avail: 'Hoy', price: 'desde $50k' },
    { name: 'Instalación de grifos', rating: 4.8, avail: 'Hoy', price: 'desde $70k' },
    { name: 'Destape de cañerías', rating: 5.0, avail: 'Mañana', price: 'desde $35k' },
];

const REVIEWS = [
    { initials: 'MA', name: 'María A.', rating: 5, time: '2 días', text: 'Excelente servicio. Llegó puntual y resolvió el problema rápidamente.' },
    { initials: 'RL', name: 'Roberto L.', rating: 4, time: '1 semana', text: 'Buen trabajo, el resultado fue perfecto. Lo volvería a contratar.' },
];

const PROFILE_TABS = ['Servicios', 'Reseñas', 'Métricas', 'Sobre mí'];

export function OffererProfilePage() {
    const navigate = useNavigate();
    const { id } = useParams();
    const { toasts, showToast } = useToast();
    const [tab, setTab] = useState(0);
    const [profile, setProfile] = useState(null);
    const [userProfile, setUserProfile] = useState(null);
    const [metrics, setMetrics] = useState(null);
    const [loadingMetrics, setLoadingMetrics] = useState(true);
    const [offererPhotoSrc, setOffererPhotoSrc] = useState(null);
    const [navbarAvatarSrc, setNavbarAvatarSrc] = useState(null);

    useEffect(() => {
        Promise.all([
            profileApi.getProfile(id),
            profileApi.getMyProfile().catch(() => null),
        ])
            .then(async ([offererData, currentUserData]) => {
                setProfile(offererData);
                setUserProfile(currentUserData);
                
                // Obtener los datos del usuario del oferente (para la foto)
                if (offererData?.userId) {
                    try {
                        const offererUserData = await profileApi.getUserById(offererData.userId);
                        setOffererPhotoSrc(getApiImageUrl(offererUserData?.profilePhotoUrl || null));
                    } catch (e) {
                        console.warn('No se pudo obtener datos del usuario oferente:', e);
                    }
                }
            })
            .catch((e) => showToast(e.message || 'No se pudo cargar tu perfil', 'danger'));
        metricsApi.getOffererMetrics(id)
            .then(setMetrics)
            .catch(() => showToast('Error al cargar métricas del oferente', 'danger'))
            .finally(() => setLoadingMetrics(false));
    }, [id, showToast]);

    useEffect(() => {
        setNavbarAvatarSrc(getApiImageUrl(userProfile?.profilePhotoUrl || null));
    }, [userProfile]);

    const openWhatsApp = () => {
        const whatsappNumber = profile?.whatsappNumber || profile?.phoneNumber;
        if (!whatsappNumber) {
            showToast('Número de WhatsApp no disponible', 'warning');
            return;
        }
        window.open(`https://wa.me/${whatsappNumber}`, '_blank');
    }

    return (
        <>
            <AppNavbar avatar={userProfile?.fullName ? userProfile.fullName.split(' ').slice(0,2).map((w)=>w[0].toUpperCase()).join('') : 'JP'} avatarSrc={navbarAvatarSrc} links={[{ to: '/services', label: '← Resultados' }]} />

            <div className="profile-hero">
                <div className="profile-top">
                    <div className="profile-av-wrap">
                        <div className="av av-xl">
                            <Avatar src={offererPhotoSrc} initials={(profile?.specialty || 'OF').slice(0, 2).toUpperCase()} alt="Foto del oferente" />
                        </div>
                        <div className="profile-verified"><Icon name="check" size={12} strokeWidth={2.5} /></div>
                    </div>
                    <div className="profile-info">
                        <div className="profile-name">{profile?.specialty ? profile.specialty : 'Perfil de oferente'}</div>
                        <div style={{ fontSize: '13px', color: 'var(--c-mid)', margin: '3px 0' }}>{profile?.publicDescription || 'Información pública del oferente'}</div>
                        <div className="profile-meta">
                            <span className="pm-item">
                                {loadingMetrics ? (
                                    <span className="loading-pulse" style={{ width: 120, height: 14, display: 'inline-block' }} />
                                ) : (
                                    <>
                                        <Stars rating={metrics?.averageRating ?? 0} size={11} />&nbsp;
                                        <strong style={{ color: 'var(--c-text)' }}>{(metrics?.averageRating ?? 0).toFixed(1)}</strong>&nbsp;
                                        ({metrics?.totalRatings ?? 0} reseñas)
                                    </>
                                )}
                            </span>
                            <span className="pm-item"><Icon name="mapPin" size={13} />Bogotá, Colombia</span>
                            <span className="pm-item"><Icon name="clock" size={13} />8 años de experiencia</span>
                            <span className="pm-item">
                                {loadingMetrics ? (
                                    <span className="loading-pulse" style={{ width: 140, height: 14, display: 'inline-block' }} />
                                ) : (
                                    <><Icon name="check" size={13} />{metrics?.totalCompletedServices ?? 0} servicios realizados</>
                                )}
                            </span>
                        </div>
                    </div>
                    <div className="profile-cta">
                        <WhatsAppButton label="WhatsApp" onClick={openWhatsApp} />
                        <button className="btn btn-primary" onClick={() => navigate(`/services?offererId=${id}`)}>Contratar servicio</button>
                    </div>
                </div>
            </div>

            <div className="profile-tabs-bar">
                <div className="profile-tabs-inner">
                    <div className="tabs" style={{ border: 'none', margin: 0 }}>
                        {PROFILE_TABS.map((t, i) => (
                            <div key={t} className={`tab ${tab === i ? 'active' : ''}`} onClick={() => setTab(i)}>{t}</div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="profile-body">
                <div>
                    <div className="card" style={{ marginBottom: '14px' }}>
                        <div className="card-h">Servicios ofrecidos</div>
                        {SERVICES.map((s) => (
                            <div className="service-row" key={s.name} onClick={() => navigate('/services/1')}>
                                <div className="s-row-ico"><Icon name="wrench" size={18} /></div>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontSize: '13px', fontWeight: 600 }}>{s.name}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--c-mid)', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <Stars rating={s.rating} showValue size={11} />
                                        <span className={`badge ${s.avail === 'Hoy' ? 'badge-success' : 'badge-warn'}`} style={{ fontSize: '10px' }}>{s.avail}</span>
                                    </div>
                                </div>
                                <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--c-primary-d)' }}>{s.price}</span>
                            </div>
                        ))}
                    </div>
                    <div className="card">
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
                            <div className="card-h" style={{ margin: 0 }}>
                                {loadingMetrics ? 'Reseñas' : `Reseñas (${metrics?.totalRatings ?? 0})`}
                            </div>
                            <div style={{ textAlign: 'center' }}>
                                <div style={{ fontSize: '28px', fontWeight: 800, lineHeight: 1 }}>
                                    {loadingMetrics ? <span className="loading-pulse" style={{ width: 36, height: 28, display: 'inline-block' }} /> : (metrics?.averageRating ?? 0).toFixed(1)}
                                </div>
                                <Stars rating={metrics?.averageRating ?? 0} />
                            </div>
                        </div>
                        {REVIEWS.map((r) => (
                            <div className="orev" key={r.initials}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '9px', marginBottom: '6px' }}>
                                    <div className="av av-sm">{r.initials}</div>
                                    <strong style={{ fontSize: '13px' }}>{r.name}</strong>
                                    <Stars rating={r.rating} size={11} />
                                    <span style={{ fontSize: '11px', color: 'var(--c-soft)', marginLeft: 'auto' }}>{r.time}</span>
                                </div>
                                <div style={{ fontSize: '13px', color: 'var(--c-mid)', lineHeight: 1.6 }}>{r.text}</div>
                            </div>
                        ))}
                        <button className="btn btn-ghost btn-full" style={{ border: '1px solid var(--c-border)', marginTop: '4px' }}>Ver todas las reseñas →</button>
                    </div>
                </div>
                <div>
                    <div className="m-grid">
                        {loadingMetrics ? (
                            <>
                                <div className="m-box"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="m-lbl">Cumplimiento</div></div>
                                <div className="m-box"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="m-lbl">Cancelaciones</div></div>
                                <div className="m-box"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="m-lbl">Reprogramados</div></div>
                                <div className="m-box"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="m-lbl">Servicios</div></div>
                            </>
                        ) : (() => {
                            const received = metrics?.totalRequestsReceived ?? 0;
                            const pct = (num) => received > 0 ? Math.round((num / received) * 100) : 0;
                            return (
                                <>
                                    <div className="m-box"><div className="m-val">{pct(metrics?.totalCompletedServices)}%</div><div className="m-lbl">Cumplimiento</div></div>
                                    <div className="m-box"><div className="m-val">{pct(metrics?.totalCancelledServices)}%</div><div className="m-lbl">Cancelaciones</div></div>
                                    <div className="m-box"><div className="m-val">{pct(metrics?.totalRescheduleProposalsSent)}%</div><div className="m-lbl">Reprogramados</div></div>
                                    <div className="m-box"><div className="m-val">{metrics?.totalCompletedServices ?? 0}</div><div className="m-lbl">Servicios</div></div>
                                </>
                            );
                        })()}
                    </div>
                    <div className="contact-card">
                        <div style={{ fontSize: '14px', fontWeight: 700, marginBottom: '3px' }}>¿Te interesa contratar?</div>
                        <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginBottom: '14px' }}>Solicita el servicio o contáctalo directamente</div>
                        <button className="btn btn-primary btn-full" style={{ marginBottom: '8px' }} onClick={() => navigate(`/services?offererId=${id}`)}>Ver servicios y solicitar</button>
                        <WhatsAppButton block label="Contactar por WhatsApp" onClick={openWhatsApp} />
                        <div className="divider" />
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '11px', color: 'var(--c-mid)' }}>
                            <Icon name="shield" size={13} style={{ color: 'var(--c-success)' }} />Perfil verificado por ServiYa
                        </div>
                    </div>
                </div>
            </div>
            <ToastContainer toasts={toasts} />
        </>
    );
}

export default OffererProfilePage;
