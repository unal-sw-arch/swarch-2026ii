import { useState } from "react";
import { Link } from 'react-router-dom';
import { Icon, ToastContainer, useToast, authApi } from '../../../../shared';

import './RecoverPasswordPage.css';

export function RecoverPasswordPage() {
    const { toasts, showToast } = useToast();
    const [email, setEmail] = useState('');
    const [sent, setSent] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleSend = async () => {
        if (!email) {
            showToast('Ingresa tu correo electrónico', 'danger');
            return;
        }
        setLoading(true);
        try {
            // RF-003: el backend responde 204 exista o no el correo (no filtra cuentas).
            await authApi.requestPasswordReset(email);
            setSent(true);
        } catch (e) {
            showToast(e.message || 'No se pudo enviar el enlace de recuperación', 'danger');
        } finally {
            setLoading(false);
        }
    };

    return (
        <>
            <nav className="nav">
                <Link to="/" className="nav-logo"><img src="/logo.svg" alt="ServiYa" style={{ height: '24px' }} /></Link>
                <div className="nav-spacer" />
                <div className="nav-actions">
                    <Link to="/login" className="btn btn-ghost btn-sm"><Icon name="chevronLeft" size={14} />Volver</Link>
                </div>
            </nav>

            <div className="rec-wrap">
                {!sent ? (
                    <div className="rec-card">
                        <div className="rec-ico"><Icon name="lock" size={30} /></div>
                        <div className="rec-title">Recuperar contraseña</div>
                        <div className="rec-text">Ingresa tu correo registrado y te enviaremos un enlace seguro de un solo uso para restablecer tu contraseña.</div>
                        <div className="input-group" style={{ textAlign: 'left' }}>
                            <label className="label">Correo electrónico</label>
                            <div className="input-wrap"><div className="input-ico"><Icon name="mail" size={15} /></div><input className="input" type="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} /></div>
                        </div>
                        <button className="btn btn-primary btn-full btn-lg" onClick={handleSend} disabled={loading}><Icon name="send" size={17} />{loading ? 'Enviando…' : 'Enviar enlace de recuperación'}</button>
                        <p className="rec-foot">¿Recordaste tu contraseña? <Link to="/login">Inicia sesión</Link></p>
                    </div>
                ) : (
                    <div className="rec-card">
                        <div className="rec-ico rec-ico-ok"><Icon name="check" size={30} /></div>
                        <div className="rec-title">¡Enlace enviado!</div>
                        <div className="rec-text">Hemos enviado un enlace de recuperación a <strong>{email}</strong>. El enlace expirará en 30 minutos y es de un solo uso.</div>
                        <Link to="/login" className="btn btn-primary btn-full btn-lg">Volver al inicio de sesión</Link>
                        <p className="rec-foot rec-foot-soft">¿No recibiste el correo? Revisa tu spam o <a onClick={handleSend} style={{ color: 'var(--c-primary)', cursor: 'pointer' }}>reenviar</a></p>
                    </div>
                )}
            </div>
            <ToastContainer toasts={toasts} />
        </>
    );
}

export default RecoverPasswordPage;
