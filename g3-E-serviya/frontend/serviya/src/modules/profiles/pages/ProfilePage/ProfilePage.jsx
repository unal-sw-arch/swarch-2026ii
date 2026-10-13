import { useState, useEffect, useRef } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Avatar, Icon, Modal, ToastContainer, useToast, CLIENT_NAV, OFFERER_NAV, profileApi, addressApi, accountApi, isAuthenticated, saveToken, clearToken, rolesFromToken, getApiImageUrl } from '../../../../shared';
import { metricsApi } from '../../../../shared/api';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';

import './ProfilePage.css';
import 'leaflet/dist/leaflet.css';
import { useForm } from "react-hook-form";

const TABS = ['Información personal', 'Mis direcciones', 'Credenciales', 'Mis métricas', 'Roles'];

function AddressModal({
    onClose,
    title,
    onSave,
    editedAddress,
    showToast,
    profile
}) {
    const [location, setLocation] = useState(null);
    const [geoLoading, setGeoLoading] = useState(false);
    const {
        register,
        handleSubmit,
        reset,
        setValue
    } = useForm({
        defaultValues: editedAddress || {}
    })

    useEffect(() => {
        if (!editedAddress) return;

        reset(editedAddress);

        setValue("main", profile?.primaryAddressId === editedAddress.id);

        if (editedAddress.latitude != null && editedAddress.longitude != null) {
            setLocation({
                latitude: editedAddress.latitude,
                longitude: editedAddress.longitude
            });
        }
    }, [editedAddress, reset]);

    useEffect(() => {
        if (!location) return;

        reverseGeocode(
            location.latitude,
            location.longitude
        );
    }, [location]);

    const reverseGeocode = async (lat, lng) => {
        setGeoLoading(true);
        try {
            const response = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}`
            );
            const data = await response.json();

            setValue("addressLine", data.display_name ?? "");
            setValue(
                "city",
                data.address?.city ||
                data.address?.town ||
                data.address?.village ||
                ""
            );
        } catch (error) {
            showToast('No fue posible obtener la dirección', 'danger');
        } finally {
            setGeoLoading(false);
        }
    };

    const useMyLocation = () => {
        if (!navigator.geolocation) {
            showToast('Tu navegador no soporta geolocalización', 'danger');
            return;
        }
        navigator.geolocation.getCurrentPosition(
            (pos) => setLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
            () => showToast('No se pudo obtener tu ubicación', 'danger')
        );
    };

    return (
        <Modal open={true} onClose={onClose}>
            <form onSubmit={handleSubmit((data) =>
                onSave({
                    ...data,
                    latitude: location?.latitude,
                    longitude: location?.longitude
                })
            )}
            >
                <div className="modal-title">{title}</div>
                <div className="modal-sub">Haz clic en el mapa o arrastra el marcador para ubicar tu dirección.</div>

                <div style={{ position: 'relative', marginBottom: '12px' }}>
                    <MapContainer
                        center={location ? [location.latitude, location.longitude] : [4.60971, -74.08175]}
                        zoom={location ? 16 : 12}
                        style={{
                            height: '280px',
                            width: '100%',
                            borderRadius: '10px',
                            border: '1px solid var(--c-border)',
                            zIndex: 0
                        }}
                    >
                        <TileLayer
                            attribution='&copy; OpenStreetMap contributors'
                            url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                        />
                        <RecenterMap location={location} />
                        <LocationMarker location={location} onLocationSelect={setLocation} />
                    </MapContainer>

                    <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        style={{
                            position: 'absolute',
                            top: '10px',
                            right: '10px',
                            zIndex: 1000,
                            background: 'var(--c-bg)',
                            border: '1px solid var(--c-border)',
                            boxShadow: '0 1px 4px rgba(0,0,0,.15)'
                        }}
                        onClick={useMyLocation}
                    >
                        <Icon name="mapPin" size={13} />Usar mi ubicación
                    </button>
                </div>

                {!location && (
                    <div className="note-box" style={{ marginBottom: '12px' }}>
                        Puedes seleccionar un punto en el mapa para obtener las coordenadas automáticamente, o escribir la dirección manualmente.
                    </div>
                )}

                <div className="input-group">
                    <label className="label">Dirección</label>
                    <div className="input-wrap">
                        <div className="input-ico"><Icon name="mapPin" size={15} /></div>
                        <input
                            className="input"
                            readOnly={geoLoading}
                            {...register("addressLine", {
                                required: true
                            })}
                        />
                    </div>
                </div>

                <div className="input-group">
                    <label className="label">Ciudad</label>
                    <input
                        className="input"
                        readOnly={geoLoading}
                        {...register("city", {
                            required: true
                        })}
                    />
                </div>

                <label className="check-line">
                    <input
                        type="checkbox"
                        readOnly={!location || geoLoading}
                        {...register("main")}
                    />
                    Establecer como dirección principal
                </label>

                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={onClose}>
                        Cancelar
                    </button>
                    <button
                        type="submit"
                        className="btn btn-primary btn-full"
                        disabled={geoLoading}
                    >
                        Guardar
                    </button>
                </div>
            </form>
        </Modal>
    );
}

function LocationMarker({ location, onLocationSelect }) {
    useMapEvents({
        click(e) {
            const { lat, lng } = e.latlng;
            onLocationSelect({ latitude: lat, longitude: lng });
        }
    });

    return location ? (
        <Marker
            position={[location.latitude, location.longitude]}
            draggable
            eventHandlers={{
                dragend: (e) => {
                    const { lat, lng } = e.target.getLatLng();
                    onLocationSelect({ latitude: lat, longitude: lng });
                }
            }}
        />
    ) : null;
}

function RecenterMap({ location }) {
    const map = useMap();

    useEffect(() => {
        if (location) {
            map.flyTo(
                [location.latitude, location.longitude],
                16,
                {
                    animate: true,
                    duration: 1
                }
            );
        }
    }, [location, map]);

    return null;
}

export function ProfilePage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [tab, setTab] = useState(0);
    const [newAddressOpen, setNewAddressOpen] = useState(false);
    const [editAddressOpen, setEditAddressOpen] = useState(false);
    const [deleteOpen, setDeleteOpen] = useState(false);
    const [profile, setProfile] = useState(null);
    const [offererProfile, setOffererProfile] = useState(null);
    const [addresses, setAddresses] = useState([]);
    const [editedAddress, setEditedAddress] = useState(null);
    const [isOfferer, setIsOfferer] = useState(false);
    const [offererForm, setOffererForm] = useState({ whatsappNumber: '', publicDescription: '', specialty: '' });
    const [savingOffererProfile, setSavingOffererProfile] = useState(false);
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const fileInputRef = useRef(null);

    const [clientMetrics, setClientMetrics] = useState(null);
    const [clientTags, setClientTags] = useState([]);
    const [offererMetrics, setOffererMetrics] = useState(null);
    const [offererTags, setOffererTags] = useState([]);
    const [loadingMetrics, setLoadingMetrics] = useState(true);

    // --- Edicion del perfil personal (RF-006) ---
    const [form, setForm] = useState({ fullName: '', phone: '', description: '' });
    const [savingProfile, setSavingProfile] = useState(false);

    // --- Roles del usuario (RF-010/011) ---
    const [roles, setRoles] = useState([]);
    const [acquiringRole, setAcquiringRole] = useState(null);

    // --- Eliminacion de cuenta (RF-008) ---
    const [deleteConfirmed, setDeleteConfirmed] = useState(false);
    const [deletingAccount, setDeletingAccount] = useState(false);

    /** RF-008: soft delete de la cuenta; el backend cancela solicitudes y desactiva servicios. */
    const handleDeleteAccount = () => {
        setDeletingAccount(true);
        accountApi.deleteMyAccount()
            .then(() => {
                clearToken(); // la sesion deja de ser valida: no se puede volver a iniciar sesion
                showToast('Tu cuenta ha sido eliminada', 'success');
                setTimeout(() => navigate('/login'), 1000);
            })
            .catch((e) => {
                showToast(e.message || 'No se pudo eliminar la cuenta', 'danger');
                setDeletingAccount(false);
            });
    };

    const hasRole = (name) => roles.includes(name);

    /**
     * RF-010/011: adquiere el rol y guarda el JWT NUEVO que devuelve el backend (ya trae el rol),
     * de modo que el acceso es inmediato sin volver a iniciar sesion.
     */
    const handleAcquireRole = (roleName) => {
        setAcquiringRole(roleName);
        const call = roleName === 'OFFERER'
            ? accountApi.acquireOffererRole()
            : accountApi.acquireClientRole();

        call.then((auth) => {
            saveToken(auth.token); // acceso inmediato: el token ya incluye el rol nuevo
            setRoles(rolesFromToken(auth.token));
            showToast(
                roleName === 'OFFERER' ? '¡Ya eres oferente! Completa tu perfil público.' : '¡Ya puedes solicitar servicios!',
                'success');
            setTimeout(() => navigate(roleName === 'OFFERER' ? '/offerer/dashboard' : '/dashboard'), 1200);
        })
            .catch((e) => showToast(e.message || 'No se pudo adquirir el rol', 'danger'))
            .finally(() => setAcquiringRole(null));
    };

    // --- Cambio de contraseña (RF-007) ---
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmNewPassword, setConfirmNewPassword] = useState('');
    const [changingPassword, setChangingPassword] = useState(false);

    // RF-005: carga la información personal del usuario autenticado (identidad tomada del JWT).
    useEffect(() => {
        if (!isAuthenticated()) {
            navigate('/login');
            return;
        }
        profileApi.getMyProfile()
            .then((data) => {
                setProfile(data);
                // RF-006: campos editables precargados con los valores actuales.
                setForm({
                    fullName: data.fullName ?? '',
                    phone: data.phoneNumber ?? '',
                    description: data.bio ?? '',
                });
                setIsOfferer(rolesFromToken().includes('OFFERER'));
            })
            .catch((e) => showToast(e.message || 'No se pudo cargar tu perfil', 'danger'));

        // RF-010/011/067: roles reales del usuario (fuente de verdad: el backend).
        accountApi.getMyRoles()
            .then((data) => setRoles(data.map((r) => r.name)))
            .catch(() => setRoles(rolesFromToken())); // fallback: los del token
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    /**
     * RF-006: guarda solo los campos que realmente cambiaron (PATCH parcial).
     * El documento no se envia nunca: es inmutable por regla de negocio.
     */
    const handleSaveProfile = () => {
        const changes = {};
        if (form.fullName !== (profile?.fullName ?? '')) changes.fullName = form.fullName;
        if (form.phone !== (profile?.phoneNumber ?? '')) changes.phone = form.phone;
        if (form.description !== (profile?.bio ?? '')) changes.description = form.description;

        if (Object.keys(changes).length === 0) {
            showToast('No hay cambios por guardar', 'warn');
            return;
        }
        if (!form.fullName.trim()) {
            showToast('El nombre no puede quedar vacío', 'danger');
            return;
        }

        setSavingProfile(true);
        profileApi.updateMyProfile(changes)
            .then((updated) => {
                setProfile(updated);
                showToast('Perfil actualizado correctamente', 'success');
            })
            .catch((e) => showToast(e.message || 'No se pudo actualizar el perfil', 'danger'))
            .finally(() => setSavingProfile(false));
    };

    useEffect(() => {
        addressApi.getMyAddresses()
            .then(setAddresses)
            .catch((e) => showToast(e.message || 'No se pudieron cargar tus direcciones', 'danger'));
    }, [profile?.id]);

    useEffect(() => {
        if (!isOfferer) return;
        profileApi.getOffererProfile()
            .then((data) => {
                setOffererProfile(data);
                setOffererForm({
                    whatsappNumber: data?.whatsappNumber || '',
                    publicDescription: data?.publicDescription || '',
                    specialty: data?.specialty || '',
                });
            })
            .catch((e) => showToast(e.message || 'No se pudo cargar el perfil de oferente', 'danger'));
    }, [isOfferer]);

    useEffect(() => {
        metricsApi.getMyMetrics()
            .then((data) => {
                const cm = data.clientMetrics;
                const om = data.offererMetrics;
                setClientMetrics(cm);
                setOffererMetrics(om);

                const tagPromises = [];
                if (cm?.clientId) {
                    tagPromises.push(
                        metricsApi.getClientTagMetrics(cm.clientId)
                            .then((tags) => setClientTags(tags || []))
                            .catch(() => {})
                    );
                }
                if (om?.offererId && isOfferer) {
                    tagPromises.push(
                        metricsApi.getOffererTagMetrics(om.offererId)
                            .then((tags) => setOffererTags(tags || []))
                            .catch(() => {})
                    );
                }
                return Promise.all(tagPromises);
            })
            .catch(() => {})
            .finally(() => setLoadingMetrics(false));
    }, [isOfferer]);

    // Iniciales para el avatar a partir del nombre completo.
    const initials = (profile?.fullName || 'U')
        .split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
    const profilePhotoSrc = getApiImageUrl(profile?.profilePhotoUrl || null);

    const openEditAddresss = (address) => () => {
        setEditedAddress({
            ...address,
        });
        setEditAddressOpen(true);
    }

    const closeAddressModal = () => {
        setNewAddressOpen(false);
        setEditAddressOpen(false);
        setEditedAddress(null);
    };

    const changeMainAddress = (addressId) => {
        return profileApi
            .changeMainAddress({ addressId })
            .then(() => {
                setAddresses(prev =>
                    prev.map(a => ({
                        ...a,
                        main: a.id === addressId
                    }))
                );

                setProfile(prev => ({
                    ...prev,
                    primaryAddressId: addressId
                }));
            })
            .catch(e => {
                showToast(
                    e.message || 'No se pudo establecer la dirección principal',
                    'danger'
                );
                throw e;
            });
    };

    const clearMainAddress = () => {
        return profileApi
            .changeMainAddress({ addressId: null })
            .then(() => {
                setAddresses(prev =>
                    prev.map(a => ({
                        ...a,
                        main: false
                    }))
                );

                setProfile(prev => ({
                    ...prev,
                    primaryAddressId: null
                }));
            })
            .catch(e => {
                showToast(
                    e.message || 'No se pudo quitar la dirección principal',
                    'danger'
                );
                throw e;
            });
    };

    const createAddress = (formData) => {
        const payload = {
            ...formData
        };

        addressApi
            .createAddress(payload)
            .then((created) => {
                setAddresses(prev => [
                    ...prev,
                    { ...created }
                ]);
                if (payload.main === true) {
                    changeMainAddress(created.id);
                }
                setNewAddressOpen(false);
                showToast('Dirección creada exitosamente', 'success');
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo crear la dirección',
                    'danger'
                )
            );
    };

    const saveEditedAddress = (formData) => {
        const payload = {
            ...editedAddress,
            ...formData
        };

        if (editedAddress.id === profile?.primaryAddressId && payload.main === false) {
            clearMainAddress();
        }

        if (editedAddress.id !== profile?.primaryAddressId && payload.main === true) {
            changeMainAddress(editedAddress.id);
        }

        addressApi
            .updateAddress(payload.id, payload)
            .then(() => {
                setAddresses(prev =>
                    prev.map(a =>
                        a.id === payload.id
                            ? payload
                            : a
                    )
                );
                setEditAddressOpen(false);
                setEditedAddress(null);
                showToast(
                    'Dirección actualizada exitosamente',
                    'success'
                );
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo actualizar la dirección',
                    'danger'
                )
            );
    }

    const deleteAddress = (address) => () => {
        if (address.id === profile?.primaryAddressId) {
            clearMainAddress();
        }

        addressApi
            .deleteAddress(address.id)
            .then(() => {
                setAddresses(prev => prev.filter(a => a.id !== address.id));
                showToast('Dirección eliminada', 'danger');
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo eliminar la dirección',
                    'danger'
                )
            );
    };

    const handleOffererProfileSave = () => {
        setSavingOffererProfile(true);
        profileApi.updateOffererProfile(offererForm)
            .then((data) => {
                setOffererProfile(data);
                setOffererForm({
                    whatsappNumber: data?.whatsappNumber || '',
                    publicDescription: data?.publicDescription || '',
                    specialty: data?.specialty || '',
                });
                showToast('Perfil público del oferente actualizado', 'success');
            })
            .catch((e) => showToast(e.message || 'No se pudo actualizar el perfil de oferente', 'danger'))
            .finally(() => setSavingOffererProfile(false));
    };

    const handleProfilePhotoPick = () => fileInputRef.current?.click();

    const handleProfilePhotoUpload = async (event) => {
        const file = event.target.files?.[0];
        if (!file) return;

        const formData = new FormData();
        formData.append('photoUrl', file);

        setUploadingPhoto(true);
        try {
            const updated = await profileApi.updateMyProfilePhoto(formData);
            setProfile(prev => prev ? { ...prev, profilePhotoUrl: updated?.profilePhotoUrl || prev.profilePhotoUrl } : prev);
            showToast('Foto de perfil actualizada', 'success');
        } catch (e) {
            showToast(e.message || 'No se pudo actualizar la foto de perfil', 'danger');
        } finally {
            setUploadingPhoto(false);
            event.target.value = '';
        }
    };

    function handleChangePassword() {
        if (!currentPassword) {
            showToast('Ingresa tu contraseña actual', 'danger');
            return;
        }
        if (newPassword.length < 8) {
            showToast('La nueva contraseña debe tener al menos 8 caracteres', 'danger');
            return;
        }
        if (newPassword !== confirmNewPassword) {
            showToast('Las contraseñas no coinciden', 'danger');
            return;
        }

        setChangingPassword(true);
        profileApi.changePassword(currentPassword, newPassword)
            .then(() => {
                showToast('Contraseña actualizada', 'success');
                setCurrentPassword('');
                setNewPassword('');
                setConfirmNewPassword('');
            })
            .catch((e) => showToast(e.message || 'No se pudo cambiar la contraseña', 'danger'))
            .finally(() => setChangingPassword(false));
    }

    return (
        <DashboardLayout sections={isOfferer ? OFFERER_NAV : CLIENT_NAV} avatar={initials} avatarSrc={profilePhotoSrc}>
            <div className="ph"><h1>Mi perfil</h1><p>Gestiona tu información personal y configuración de cuenta</p></div>

            <div className="tabs">
                {TABS.map((t, i) => (
                    <div key={t} className={`tab ${tab === i ? 'active' : ''}`} onClick={() => setTab(i)}>{t}</div>
                ))}
            </div>

            {tab === 0 && (
                <div className="card">
                    <div className="profile-id">
                        <div style={{ position: 'relative' }}>
                            <div className="av av-xl">
                                <Avatar src={profilePhotoSrc} initials={initials} alt="Foto de perfil" />
                            </div>
                            <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleProfilePhotoUpload} />
                            <button className="avatar-edit" onClick={handleProfilePhotoPick} disabled={uploadingPhoto}>
                                {uploadingPhoto ? '…' : <Icon name="camera" size={12} strokeWidth={2.5} />}
                            </button>
                        </div>
                        <div>
                            <div style={{ fontSize: '18px', fontWeight: 700 }}>{profile?.fullName ?? 'Cargando…'}</div>
                            <div style={{ fontSize: '13px', color: 'var(--c-mid)' }}>{profile?.profileType === 'COMPANY' ? 'Empresa' : 'Persona natural'}</div>
                            <span className="badge badge-success" style={{ marginTop: '4px' }}>Cuenta activa</span>
                        </div>
                    </div>
                    <div className="g2">
                        <div className="input-group">
                            <label className="label">Nombre completo</label>
                            <input className="input" value={form.fullName}
                                onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
                        </div>
                        <div className="input-group">
                            <label className="label">Teléfono</label>
                            <div className="input-wrap">
                                <div className="input-ico"><Icon name="phone" size={15} /></div>
                                <input className="input" value={form.phone}
                                    onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                            </div>
                        </div>
                    </div>
                    <div className="g2">
                        <div className="input-group"><label className="label">Tipo de documento</label><input className="input" value={profile?.documentType ?? ''} readOnly disabled /></div>
                        <div className="input-group"><label className="label">Número de documento</label><input className="input" value={profile?.documentNumber ?? ''} readOnly disabled /></div>
                    </div>
                    <div className="input-group"><label className="label">Tipo de perfil</label><input className="input" value={profile?.profileType === 'COMPANY' ? 'Empresa' : 'Persona natural'} readOnly disabled /></div>
                    <div className="input-group">
                        <label className="label">Descripción personal</label>
                        <textarea className="input" rows="3" value={form.description}
                            onChange={(e) => setForm({ ...form, description: e.target.value })} />
                    </div>
                    <div className="note-box"><strong style={{ color: 'var(--c-text)' }}>Datos no editables:</strong> Tipo y número de documento — se establecen al registrarte y se guardan cifrados (AES-256-GCM). Tu teléfono también viaja cifrado (RF-005/RF-006).</div>
                    <button className="btn btn-primary" onClick={handleSaveProfile} disabled={savingProfile}>
                        <Icon name="save" size={15} />{savingProfile ? 'Guardando…' : 'Guardar cambios'}
                    </button>

                    {isOfferer && (
                        <div className="card" style={{ marginTop: '16px' }}>
                            <div className="card-title">Perfil público del oferente</div>
                            <div className="input-group">
                                <label className="label">WhatsApp</label>
                                <input className="input" value={offererForm.whatsappNumber} onChange={(e) => setOffererForm(prev => ({ ...prev, whatsappNumber: e.target.value }))} />
                            </div>
                            <div className="input-group">
                                <label className="label">Descripción pública</label>
                                <textarea className="input" rows={4} value={offererForm.publicDescription} onChange={(e) => setOffererForm(prev => ({ ...prev, publicDescription: e.target.value }))} />
                            </div>
                            <div className="input-group">
                                <label className="label">Especialidad</label>
                                <input className="input" value={offererForm.specialty} onChange={(e) => setOffererForm(prev => ({ ...prev, specialty: e.target.value }))} />
                            </div>
                            <button className="btn btn-primary" onClick={handleOffererProfileSave} disabled={savingOffererProfile}>
                                {savingOffererProfile ? 'Guardando...' : 'Guardar perfil público'}
                            </button>
                        </div>
                    )}
                </div>
            )}

            {tab === 1 && (
                <div>
                    <div className="page-head">
                        <div style={{ fontSize: '14px', fontWeight: 700 }}>Mis direcciones</div>
                        <button className="btn btn-primary btn-sm" onClick={() => setNewAddressOpen(true)}><Icon name="plus" size={13} />Agregar dirección</button>
                    </div>
                    {addresses.map((a, i) => (
                        <div className="card addr-card" key={i} style={a.id === profile?.primaryAddressId ? { borderLeft: '3px solid var(--c-primary)' } : undefined}>
                            <div className="addr-row">
                                <div className="stat-ico" style={{ margin: 0, flexShrink: 0, ...(a.id === profile?.primaryAddressId ? {} : { background: 'var(--c-bg-s)', color: 'var(--c-soft)' }) }}><Icon name="mapPin" size={18} /></div>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontSize: '13px', fontWeight: 700 }}>{a.addressLine} {a.id === profile?.primaryAddressId && <span className="badge badge-primary">Principal</span>}</div>
                                    <div style={{ fontSize: '12px', color: 'var(--c-mid)', marginTop: '3px' }}>{a.city}</div>
                                </div>
                                <div style={{ display: 'flex', gap: '5px' }}>
                                    <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={openEditAddresss(a)}><Icon name="edit" size={13} /></button>
                                    <button className="btn btn-danger btn-sm" onClick={deleteAddress(a)}><Icon name="trash" size={13} /></button>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {tab === 2 && (
                <div>
                    <div className="card" style={{ marginBottom: '14px' }}>
                        <div className="card-title">Cambiar correo electrónico</div>
                        <div className="input-group"><label className="label">Correo actual</label><input className="input" defaultValue="jp@email.com" disabled /></div>
                        <div className="input-group"><label className="label">Nuevo correo</label><input className="input" type="email" placeholder="nuevocorreo@ejemplo.com" /></div>
                        <div className="input-group"><label className="label">Confirmar contraseña actual</label><input className="input" type="password" placeholder="••••••••" /></div>
                        <button className="btn btn-primary" onClick={() => showToast('Correo actualizado', 'success')}>Cambiar correo</button>
                    </div>
                    <div className="card">
                        <div className="card-title">Cambiar contraseña</div>
                        <div className="input-group">
                            <label className="label">Contraseña actual</label>
                            <input
                                className="input"
                                type="password"
                                placeholder="••••••••"
                                value={currentPassword}
                                onChange={(e) => setCurrentPassword(e.target.value)}
                            />
                        </div>
                        <div className="input-group">
                            <label className="label">Nueva contraseña</label>
                            <input
                                className="input"
                                type="password"
                                placeholder="Mínimo 8 caracteres"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                            />
                        </div>
                        <div className="input-group">
                            <label className="label">Confirmar nueva contraseña</label>
                            <input
                                className="input"
                                type="password"
                                placeholder="Repite la contraseña"
                                value={confirmNewPassword}
                                onChange={(e) => setConfirmNewPassword(e.target.value)}
                            />
                        </div>
                        <button className="btn btn-primary" onClick={handleChangePassword} disabled={changingPassword}>
                            {changingPassword ? 'Cambiando...' : 'Cambiar contraseña'}
                        </button>
                    </div>
                    <div className="danger-zone">
                        <div className="danger-title">Zona de peligro</div>
                        <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '14px' }}>Eliminar permanentemente tu cuenta. Esta acción no se puede deshacer (RF-008).</div>
                        <button className="btn btn-danger" onClick={() => setDeleteOpen(true)}><Icon name="trash" size={15} />Eliminar mi cuenta</button>
                    </div>
                </div>
            )}

            {tab === 3 && (
                <div>
                    {hasRole('CLIENT') && (
                        <>
                            <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Métricas como cliente</div>
                            <div className="g4" style={{ marginBottom: '18px' }}>
                                {loadingMetrics ? (
                                    <>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Cumplimiento</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Cancelaciones</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Reprogramaciones</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Mi calificación</div></div>
                                    </>
                                ) : (() => {
                                    const sent = clientMetrics?.totalRequestsSent ?? 0;
                                    const pct = (num) => sent > 0 ? Math.round((num / sent) * 100) + '%' : '0%';
                                    const cards = [
                                        { icon: 'check', cls: 'success', n: pct(clientMetrics?.totalCompletedRequests), l: 'Cumplimiento' },
                                        { icon: 'close', cls: 'danger', n: pct(clientMetrics?.totalCancelledRequests), l: 'Cancelaciones' },
                                        { icon: 'reschedule', cls: 'warn', n: pct(clientMetrics?.totalRescheduledRequests), l: 'Reprogramaciones' },
                                        { icon: 'star', cls: '', n: (clientMetrics?.averageRating ?? 0).toFixed(1) + '★', l: 'Mi calificación' },
                                    ];
                                    return cards.map((m) => (
                                        <div className="stat-card" key={m.l}>
                                            <div className={`stat-ico ${m.cls}`}><Icon name={m.icon} size={18} fill={m.icon === 'star' ? 'currentColor' : 'none'} /></div>
                                            <div className="stat-n">{m.n}</div>
                                            <div className="stat-l">{m.l}</div>
                                        </div>
                                    ));
                                })()}
                            </div>
                            <div className="card" style={{ marginBottom: hasRole('OFFERER') ? '22px' : 0 }}>
                                <div className="card-title">Etiquetas recibidas de oferentes</div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '7px' }}>
                                    {loadingMetrics ? (
                                        <span className="loading-pulse" style={{ width: 120, height: 24, display: 'inline-block' }} />
                                    ) : clientTags.length === 0 ? (
                                        <span style={{ fontSize: '13px', color: 'var(--c-mid)' }}>Aún no has recibido etiquetas</span>
                                    ) : clientTags.map((t) => (
                                        <span key={t.tagId} className={`profile-tag ${t.positive ? 'pos' : 'neg'}`}>{t.tagName} ({t.tagCount})</span>
                                    ))}
                                </div>
                            </div>
                        </>
                    )}

                    {hasRole('OFFERER') && (
                        <>
                            <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px', marginTop: hasRole('CLIENT') ? '8px' : 0 }}>Métricas como oferente</div>
                            <div className="g4" style={{ marginBottom: '18px' }}>
                                {loadingMetrics ? (
                                    <>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Cumplimiento</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Cancelaciones</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Reprogramados</div></div>
                                        <div className="stat-card"><div className="loading-pulse" style={{ width: 40, height: 22 }} /><div className="stat-l">Calificación</div></div>
                                    </>
                                ) : (() => {
                                    const received = offererMetrics?.totalRequestsReceived ?? 0;
                                    const pct = (num) => received > 0 ? Math.round((num / received) * 100) + '%' : '0%';
                                    const cards = [
                                        { icon: 'check', cls: 'success', n: pct(offererMetrics?.totalCompletedServices), l: 'Cumplimiento' },
                                        { icon: 'close', cls: 'danger', n: pct(offererMetrics?.totalCancelledServices), l: 'Cancelaciones' },
                                        { icon: 'reschedule', cls: 'warn', n: String(offererMetrics?.totalRescheduleProposalsSent ?? 0), l: 'Reprogramados' },
                                        { icon: 'star', cls: '', n: (offererMetrics?.averageRating ?? 0).toFixed(1) + '★', l: 'Calificación' },
                                    ];
                                    return cards.map((m) => (
                                        <div className="stat-card" key={m.l}>
                                            <div className={`stat-ico ${m.cls}`}><Icon name={m.icon} size={18} fill={m.icon === 'star' ? 'currentColor' : 'none'} /></div>
                                            <div className="stat-n">{m.n}</div>
                                            <div className="stat-l">{m.l}</div>
                                        </div>
                                    ));
                                })()}
                            </div>
                            <div className="card">
                                <div className="card-title">Etiquetas recibidas de clientes</div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '7px' }}>
                                    {loadingMetrics ? (
                                        <span className="loading-pulse" style={{ width: 120, height: 24, display: 'inline-block' }} />
                                    ) : offererTags.length === 0 ? (
                                        <span style={{ fontSize: '13px', color: 'var(--c-mid)' }}>Aún no has recibido etiquetas</span>
                                    ) : offererTags.map((t) => (
                                        <span key={t.tagId} className={`profile-tag ${t.positive ? 'pos' : 'neg'}`}>{t.tagName} ({t.tagCount})</span>
                                    ))}
                                </div>
                            </div>
                        </>
                    )}

                    {!hasRole('CLIENT') && !hasRole('OFFERER') && (
                        <div className="card" style={{ textAlign: 'center', padding: '20px', color: 'var(--c-mid)', fontSize: '13px' }}>
                            Adquiere un rol para ver tus métricas
                        </div>
                    )}
                </div>
            )}

            {tab === 4 && (
                <div className="card">
                    <div className="card-title" style={{ marginBottom: '4px' }}>Tus roles actuales</div>
                    <div style={{ fontSize: '13px', color: 'var(--c-mid)', marginBottom: '16px' }}>Un mismo usuario puede ser cliente y oferente sin crear otra cuenta.</div>
                    {/* RF-067 (vista propia) + RF-010/011: estado real de cada rol segun el backend. */}
                    {[
                        { id: 'CLIENT', label: 'Cliente', icon: 'user', desc: 'Puedes buscar y contratar servicios', cta: 'Solicitar servicios' },
                        { id: 'OFFERER', label: 'Oferente', icon: 'wrench', desc: 'Ofrece tus servicios y llega a más clientes', cta: 'Conviértete en oferente' },
                    ].map((r) => (
                        <div key={r.id} className={`role-row ${hasRole(r.id) ? 'role-active' : 'role-inactive'}`}>
                            <div className="stat-ico" style={hasRole(r.id) ? { margin: 0 } : { margin: 0, background: 'var(--c-bg-s)', color: 'var(--c-soft)' }}>
                                <Icon name={r.icon} size={18} />
                            </div>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: 700, color: 'var(--c-text)' }}>{r.label}</div>
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{r.desc}</div>
                            </div>
                            {hasRole(r.id)
                                ? <span className="badge badge-success">Activo</span>
                                : (
                                    <button className="btn btn-primary btn-sm"
                                        disabled={acquiringRole === r.id}
                                        onClick={() => handleAcquireRole(r.id)}>
                                        {acquiringRole === r.id ? 'Adquiriendo…' : r.cta}
                                    </button>
                                )}
                        </div>
                    ))}
                    {hasRole('ADMIN') && (
                        <div className="role-row role-active">
                            <div className="stat-ico" style={{ margin: 0 }}><Icon name="shield" size={18} /></div>
                            <div style={{ flex: 1 }}>
                                <div style={{ fontWeight: 700 }}>Administrador</div>
                                <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>Solo otro administrador puede conceder este rol</div>
                            </div>
                            <span className="badge badge-success">Activo</span>
                        </div>
                    )}
                </div>
            )}

            {newAddressOpen && (
                <AddressModal
                    onClose={closeAddressModal}
                    title="Agregar dirección"
                    onSave={createAddress}
                    editedAddress={null}
                    showToast={showToast}
                    profile={profile}
                />
            )}
            {editAddressOpen && editedAddress && (
                <AddressModal
                    onClose={closeAddressModal}
                    title="Editar dirección"
                    onSave={saveEditedAddress}
                    editedAddress={editedAddress}
                    showToast={showToast}
                    profile={profile}
                />
            )}

            <Modal open={deleteOpen} onClose={() => setDeleteOpen(false)}>
                <div className="modal-title" style={{ color: 'var(--c-danger)' }}>Eliminar cuenta</div>
                <div className="modal-sub">Esta acción es irreversible. Tu cuenta será marcada como eliminada y no podrás volver a iniciar sesión.</div>
                {/* RF-008: el usuario debe conocer las consecuencias en cascada antes de confirmar. */}
                <div className="note-box" style={{ marginBottom: '12px' }}>
                    Al eliminar tu cuenta:
                    <ul style={{ margin: '6px 0 0 16px' }}>
                        <li>Tus servicios publicados quedarán desactivados.</li>
                        <li>Tus solicitudes pendientes y aceptadas se cancelarán.</li>
                        <li>Se notificará a la otra parte de cada solicitud cancelada.</li>
                    </ul>
                </div>
                <label className="check-line">
                    <input type="checkbox" checked={deleteConfirmed}
                        onChange={(e) => setDeleteConfirmed(e.target.checked)} />
                    Entiendo las consecuencias y quiero eliminar mi cuenta permanentemente
                </label>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setDeleteOpen(false)}>Cancelar</button>
                    <button className="btn btn-danger btn-full"
                        disabled={!deleteConfirmed || deletingAccount}
                        onClick={handleDeleteAccount}>
                        {deletingAccount ? 'Eliminando…' : 'Eliminar definitivamente'}
                    </button>
                </div>
            </Modal>
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default ProfilePage;
