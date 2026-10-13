import { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Icon, ToastContainer, useToast, authApi } from '../../../../shared';

import '../RecoverPasswordPage/RecoverPasswordPage.css';

export function ResetPasswordPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [params] = useSearchParams();
    const token = params.get('token');

    // 'validating' | 'valid' | 'invalid' | 'done' — sin token no hay nada que validar.
    const [status, setStatus] = useState(token ? 'validating' : 'invalid');
    const [password, setPassword] = useState('');
    const [confirm, setConfirm] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!token) return;
        authApi.validateResetToken(token)
            .then(() => setStatus('valid'))
            .catch(() => setStatus('invalid'));
    }, [token]);

    const handleConfirm = async () => {
        if (password.length < 8) {
            showToast('La contraseña debe tener al menos 8 caracteres', 'danger');
            return;
        }
        if (password !== confirm) {
            showToast('Las contraseñas no coinciden', 'danger');
            return;
        }
        setSubmitting(true);
        try {
            await authApi.confirmPasswordReset(token, password);
            setStatus('done');
        } catch (e) {
            // Token pudo expirar entre validar y confirmar.
            showToast(e.message || 'No se pudo restablecer la contraseña', 'danger');
            setStatus('invalid');
        } finally {
            setSubmitting(false);
        }
    };

    const shell = (children) => (
        <>
            <nav className="nav">
                <Link to="/" className="nav-logo"><img src="/logo.svg" alt="ServiYa" style={{ height: '24px' }} /></Link>
                <div className="nav-spacer" />
                <div className="nav-actions">
                    <Link to="/login" className="btn btn-ghost btn-sm"><Icon name="chevronLeft" size={14} />Ir a iniciar sesión</Link>
                </div>
            </nav>
            <div className="rec-wrap">{children}</div>
            <ToastContainer toasts={toasts} />
        </>
    );

    if (status === 'validating') {
        return shell(
            <div className="rec-card">
                <div className="rec-ico"><Icon name="lock" size={30} /></div>
                <div className="rec-title">Validando enlace…</div>
                <div className="rec-text">Un momento mientras comprobamos tu enlace de recuperación.</div>
            </div>
        );
    }

    if (status === 'invalid') {
        return shell(
            <div className="rec-card">
                <div className="rec-ico" style={{ background: 'var(--c-danger-bg)', color: 'var(--c-danger)' }}><Icon name="xCircle" size={30} /></div>
                <div className="rec-title">Enlace inválido o expirado</div>
                <div className="rec-text">Este enlace de recuperación no es válido o ya venció (los enlaces expiran a los 30 minutos y son de un solo uso).</div>
                <Link to="/recover" className="btn btn-primary btn-full btn-lg">Solicitar un nuevo enlace</Link>
            </div>
        );
    }

    if (status === 'done') {
        return shell(
            <div className="rec-card">
                <div className="rec-ico rec-ico-ok"><Icon name="check" size={30} /></div>
                <div className="rec-title">¡Contraseña restablecida!</div>
                <div className="rec-text">Ya puedes iniciar sesión con tu nueva contraseña.</div>
                <button className="btn btn-primary btn-full btn-lg" onClick={() => navigate('/login')}>Iniciar sesión</button>
            </div>
        );
    }

    // status === 'valid'
    return shell(
        <div className="rec-card">
            <div className="rec-ico"><Icon name="lock" size={30} /></div>
            <div className="rec-title">Nueva contraseña</div>
            <div className="rec-text">Elige una contraseña de al menos 8 caracteres.</div>
            <div className="input-group" style={{ textAlign: 'left' }}>
                <label className="label">Nueva contraseña</label>
                <div className="input-wrap">
                    <div className="input-ico"><Icon name="lock" size={15} /></div>
                    <input className="input" type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
                </div>
            </div>
            <div className="input-group" style={{ textAlign: 'left' }}>
                <label className="label">Confirmar contraseña</label>
                <div className="input-wrap">
                    <div className="input-ico"><Icon name="lock" size={15} /></div>
                    <input className="input" type="password" placeholder="••••••••" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
                </div>
            </div>
            <button className="btn btn-primary btn-full btn-lg" onClick={handleConfirm} disabled={submitting}>
                <Icon name="check" size={17} />{submitting ? 'Guardando…' : 'Restablecer contraseña'}
            </button>
            <p className="rec-foot">¿Recordaste tu contraseña? <Link to="/login">Inicia sesión</Link></p>
        </div>
    );
}

export default ResetPasswordPage;
