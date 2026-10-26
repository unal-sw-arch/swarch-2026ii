import { useState } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import { Icon, ToastContainer, useToast, authApi, saveToken, rolesFromToken, homePathForRoles } from '../../../../shared';

import './RegisterPage.css';
import 'leaflet/dist/leaflet.css';

/** Documentos aceptados. El tipo/número quedan FIJOS al registrarse: después son inmutables (RF-006). */
const DOCUMENT_TYPES = ['CC', 'CE', 'NIT', 'PASAPORTE'];

/** Recentra el mapa cuando cambia la ubicación elegida. */
function RecenterMap({ location }) {
    const map = useMap();
    if (location) map.setView([location.latitude, location.longitude], 16);
    return null;
}

/** Coloca el marcador donde el usuario hace clic en el mapa. */
function LocationMarker({ location, onSelect }) {
    useMapEvents({
        click(e) { onSelect({ latitude: e.latlng.lat, longitude: e.latlng.lng }); },
    });
    return location ? <Marker position={[location.latitude, location.longitude]} /> : null;
}

export function RegisterPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [step, setStep] = useState(1);
    const [type, setType] = useState('c');
    const [terms, setTerms] = useState(false);
    const [dataConsent, setDataConsent] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Datos del paso 2
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [phone, setPhone] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    // Documento (paso 2): se fija aquí y ya no se puede cambiar nunca más.
    const [documentType, setDocumentType] = useState('CC');
    const [documentNumber, setDocumentNumber] = useState('');

    // Dirección principal (paso 3): queda registrada en "Mis direcciones".
    const [addressLine, setAddressLine] = useState('');
    const [city, setCity] = useState('');
    const [location, setLocation] = useState(null);   // { latitude, longitude }
    const [geoLoading, setGeoLoading] = useState(false);

    /** Al elegir un punto en el mapa, se resuelve la dirección legible (igual que en "Mis direcciones"). */
    const pickLocation = async (coords) => {
        setLocation(coords);
        setGeoLoading(true);
        try {
            const res = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${coords.latitude}&lon=${coords.longitude}`);
            const data = await res.json();
            setAddressLine(data.display_name ?? '');
            setCity(data.address?.city || data.address?.town || data.address?.village || '');
        } catch {
            showToast('No se pudo obtener la dirección; puedes escribirla a mano', 'warn');
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
            (pos) => pickLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
            () => showToast('No se pudo obtener tu ubicación', 'danger'));
    };

    const handleRegister = async () => {
        // RF-004: el consentimiento de datos es obligatorio.
        if (!terms || !dataConsent) {
            showToast('Debes aceptar los términos y la política de datos', 'danger');
            return;
        }
        if (!firstName || !email || !password) {
            showToast('Completa nombre, correo y contraseña (paso 2)', 'danger');
            setStep(2);
            return;
        }
        // El documento se fija al registrarse y es inmutable después: obligatorio y validado aquí.
        if (!documentType || !documentNumber.trim()) {
            showToast('Indica tu tipo y número de documento (paso 2)', 'danger');
            setStep(2);
            return;
        }
        if (password.length < 8) {
            showToast('La contraseña debe tener al menos 8 caracteres', 'danger');
            setStep(2);
            return;
        }
        if (password !== confirmPassword) {
            showToast('Las contraseñas no coinciden', 'danger');
            setStep(2);
            return;
        }

        setSubmitting(true);
        try {
            // RF-002: solo CLIENT u OFFERER desde el registro público.
            const auth = await authApi.register({
                email,
                password,
                fullName: `${firstName} ${lastName}`.trim(),
                role: type === 'o' ? 'OFFERER' : 'CLIENT',
                documentType,
                documentNumber,
                phone,
                acceptedTerms: dataConsent,
                // Dirección opcional: los 4 campos viajan juntos (la BD exige coordenadas).
                // El backend la crea en la MISMA transacción y la deja como dirección principal.
                ...(location && addressLine && city
                    ? {
                        addressLine,
                        city,
                        latitude: location.latitude,
                        longitude: location.longitude,
                    }
                    : {}),
            });
            saveToken(auth.token);
            showToast('¡Cuenta creada! Redirigiendo...', 'success');
            const roles = rolesFromToken(auth.token);
            setTimeout(() => navigate(roles.length ? homePathForRoles(roles) : '/dashboard'), 1200);
        } catch (e) {
            showToast(e.message || 'No se pudo crear la cuenta', 'danger');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <>
            <nav className="nav">
                <Link to="/" className="nav-logo"><img src="/logo.svg" alt="ServiYa" style={{ height: '24px' }} /></Link>
                <div className="nav-spacer" />
                <div className="nav-actions">
                    <span style={{ fontSize: '13px', color: 'var(--c-mid)' }} className="hide-mob">¿Ya tienes cuenta?</span>
                    <Link to="/login" className="btn btn-outline btn-sm">Iniciar sesión</Link>
                </div>
            </nav>

            <div className="reg-wrap">
                <div className="reg-card">
                    <div style={{ textAlign: 'center', marginBottom: '22px' }}>
                        <img src="/logo.svg" style={{ height: '30px', margin: '0 auto 16px' }} alt="ServiYa" />
                        <div style={{ fontSize: '22px', fontWeight: 800, marginBottom: '3px' }}>Crear cuenta</div>
                        <div style={{ fontSize: '13px', color: 'var(--c-mid)' }}>Únete a miles de usuarios en ServiYa</div>
                    </div>

                    <div className="step-dots">
                        {[1, 2, 3, 4].map((n) => (
                            <div key={n} className={`sdot-step ${step === n ? 'active' : ''}`} />
                        ))}
                    </div>

                    {step === 1 && (
                        <div>
                            <p className="reg-q">¿Cómo quieres unirte?</p>
                            <div className="type-sel">
                                <div className={`type-card ${type === 'c' ? 'active' : ''}`} onClick={() => setType('c')}>
                                    <Icon name="user" size={30} style={{ color: 'var(--c-primary)', margin: '0 auto 8px' }} />
                                    <div className="type-card-name">Cliente</div>
                                    <div className="type-card-desc">Busca y contrata servicios para tu hogar</div>
                                </div>
                                <div className={`type-card ${type === 'o' ? 'active' : ''}`} onClick={() => setType('o')}>
                                    <Icon name="wrench" size={30} style={{ color: 'var(--c-primary)', margin: '0 auto 8px' }} />
                                    <div className="type-card-name">Oferente</div>
                                    <div className="type-card-desc">Ofrece tus servicios y llega a más clientes</div>
                                </div>
                            </div>
                            <p className="reg-note">Puedes cambiar de rol más adelante desde tu perfil</p>
                            <button className="btn btn-primary btn-full btn-lg" onClick={() => setStep(2)}>
                                Continuar<Icon name="chevronRight" size={17} />
                            </button>
                        </div>
                    )}

                    {step === 2 && (
                        <div>
                            <div className="g2">
                                <div className="input-group"><label className="label">Nombre</label><input className="input" placeholder="Juan" value={firstName} onChange={(e) => setFirstName(e.target.value)} /></div>
                                <div className="input-group"><label className="label">Apellido</label><input className="input" placeholder="Pérez" value={lastName} onChange={(e) => setLastName(e.target.value)} /></div>
                            </div>
                            <div className="input-group">
                                <label className="label">Correo electrónico</label>
                                <div className="input-wrap"><div className="input-ico"><Icon name="mail" size={15} /></div><input className="input" type="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
                            </div>
                            <div className="input-group">
                                <label className="label">Teléfono</label>
                                <div className="input-wrap"><div className="input-ico"><Icon name="phone" size={15} /></div><input className="input" type="tel" placeholder="+57 300 000 0000" value={phone} onChange={(e) => setPhone(e.target.value)} /></div>
                            </div>
                            {/* Documento: se fija aquí y luego es INMUTABLE (no se puede editar en el perfil). */}
                            <div className="g2">
                                <div className="input-group">
                                    <label className="label">Tipo de documento</label>
                                    <select className="input" value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
                                        {DOCUMENT_TYPES.map((d) => <option key={d} value={d}>{d}</option>)}
                                    </select>
                                </div>
                                <div className="input-group">
                                    <label className="label">Número de documento</label>
                                    <input className="input" placeholder="1020304050" value={documentNumber}
                                        onChange={(e) => setDocumentNumber(e.target.value)} />
                                </div>
                            </div>
                            <p className="reg-note" style={{ marginTop: 0 }}>
                                Tu documento se guarda cifrado (AES-256-GCM) y <strong>no podrá modificarse después</strong>.
                            </p>
                            <div className="input-group">
                                <label className="label">Contraseña</label>
                                <div className="input-wrap"><div className="input-ico"><Icon name="lock" size={15} /></div><input className="input" type="password" placeholder="Mínimo 8 caracteres" value={password} onChange={(e) => setPassword(e.target.value)} /></div>
                            </div>
                            <div className="input-group" style={{ marginBottom: '20px' }}>
                                <label className="label">Confirmar contraseña</label>
                                <div className="input-wrap"><div className="input-ico"><Icon name="lock" size={15} /></div><input className="input" type="password" placeholder="Repite tu contraseña" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} /></div>
                            </div>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(1)}><Icon name="chevronLeft" size={15} />Atrás</button>
                                <button className="btn btn-primary btn-full btn-lg" onClick={() => setStep(3)}>Continuar<Icon name="chevronRight" size={17} /></button>
                            </div>
                        </div>
                    )}

                    {step === 3 && (
                        <div>
                            <p className="reg-q">¿Dónde prestas o recibes los servicios?</p>
                            <p className="reg-note" style={{ marginTop: 0 }}>
                                Haz clic en el mapa (o usa tu ubicación) para fijar tu dirección principal.
                                Quedará guardada en <strong>Mis direcciones</strong> y podrás agregar más después.
                            </p>

                            <div style={{ position: 'relative', marginBottom: '12px' }}>
                                <MapContainer
                                    center={location ? [location.latitude, location.longitude] : [4.60971, -74.08175]}
                                    zoom={location ? 16 : 12}
                                    style={{ height: '240px', width: '100%', borderRadius: '10px', border: '1px solid var(--c-border)', zIndex: 0 }}
                                >
                                    <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" />
                                    <RecenterMap location={location} />
                                    <LocationMarker location={location} onSelect={pickLocation} />
                                </MapContainer>
                                <button type="button" className="btn btn-ghost btn-sm"
                                    style={{ position: 'absolute', top: '10px', right: '10px', zIndex: 1000, background: 'var(--c-bg)', border: '1px solid var(--c-border)' }}
                                    onClick={useMyLocation}>
                                    <Icon name="mapPin" size={13} />Usar mi ubicación
                                </button>
                            </div>

                            <div className="input-group">
                                <label className="label">Dirección</label>
                                <div className="input-wrap">
                                    <div className="input-ico"><Icon name="mapPin" size={15} /></div>
                                    <input className="input" placeholder={geoLoading ? 'Buscando dirección…' : 'Selecciona un punto en el mapa'}
                                        value={addressLine} onChange={(e) => setAddressLine(e.target.value)} />
                                </div>
                            </div>
                            <div className="input-group">
                                <label className="label">Ciudad</label>
                                <input className="input" placeholder="Bogotá" value={city} onChange={(e) => setCity(e.target.value)} />
                            </div>

                            <p className="reg-note" style={{ marginTop: 0 }}>
                                Tu dirección se guarda cifrada (AES-256-GCM). Este paso es opcional: puedes omitirlo y agregarla luego desde tu perfil.
                            </p>

                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(2)}><Icon name="chevronLeft" size={15} />Atrás</button>
                                <button className="btn btn-primary btn-full btn-lg" onClick={() => setStep(4)}>
                                    {location && addressLine ? 'Continuar' : 'Omitir por ahora'}<Icon name="chevronRight" size={17} />
                                </button>
                            </div>
                        </div>
                    )}

                    {step === 4 && (
                        <div>
                            <p className="reg-q">Consentimiento de uso de datos</p>
                            <div className="consent-box">
                                <label><input type="checkbox" checked={terms} onChange={(e) => setTerms(e.target.checked)} /> Acepto los <a>Términos de uso</a> y la <a>Política de privacidad</a> de ServiYa.</label>
                            </div>
                            <div className="consent-box">
                                <label><input type="checkbox" checked={dataConsent} onChange={(e) => setDataConsent(e.target.checked)} /> Autorizo el uso de mis <strong>datos personales (PII)</strong> conforme a la <a>Política de tratamiento de datos</a>. Entiendo que mis datos como documento, teléfono y dirección serán cifrados con AES-256-GCM.</label>
                            </div>
                            <div className="consent-box">
                                <label><input type="checkbox" /> Deseo recibir notificaciones y novedades de ServiYa por correo. <span style={{ color: 'var(--c-soft)' }}>(Opcional)</span></label>
                            </div>
                            <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
                                <button className="btn btn-ghost btn-full" onClick={() => setStep(3)}><Icon name="chevronLeft" size={15} />Atrás</button>
                                <button className="btn btn-primary btn-full btn-lg" onClick={handleRegister} disabled={submitting}><Icon name="check" size={17} />{submitting ? 'Creando…' : 'Crear cuenta'}</button>
                            </div>
                            <p className="reg-foot">¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link></p>
                        </div>
                    )}
                </div>
            </div>
            <ToastContainer toasts={toasts} />
        </>
    );
}

export default RegisterPage;
