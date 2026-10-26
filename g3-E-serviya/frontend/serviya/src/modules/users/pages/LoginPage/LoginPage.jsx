import { useState } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { Icon, authApi, saveToken, rolesFromToken, homePathForRoles } from '../../../../shared';

import './auth.css';
import './LoginPage.css';

const ROLES = [
    { id: 'cliente', label: 'Cliente', icon: 'user' },
    { id: 'oferente', label: 'Oferente', icon: 'wrench' },
    { id: 'admin', label: 'Admin', icon: 'shield' },
];

const FEATURES = [
    { icon: 'calendar', text: 'Agenda servicios en minutos según la disponibilidad del oferente' },
    { icon: 'star', text: 'Profesionales verificados y calificados por la comunidad' },
    { icon: 'bell', text: 'Notificaciones en tiempo real por sistema interno y correo' },
];

const ROLE_HOME = {
    cliente: '/dashboard',
    oferente: '/offerer/dashboard',
    admin: '/admin/dashboard',
};

export function LoginPage() {
    const navigate = useNavigate();
    const [role, setRole] = useState('cliente');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleLogin = async () => {
        if (!email || !password) {
            setError('Ingresa tu correo y contraseña.');
            return;
        }
        setLoading(true);
        setError('');
        try {
            // RF-001: el backend valida la contraseña (bcrypt) y bloquea cuentas baneadas/eliminadas.
            const auth = await authApi.login(email, password);
            saveToken(auth.token);
            // Navega según el rol real que viene en el JWT; si no, usa la pestaña elegida.
            const roles = rolesFromToken(auth.token);
            navigate(roles.length ? homePathForRoles(roles) : ROLE_HOME[role]);
        } catch (e) {
            setError(e.message || 'Correo o contraseña incorrectos.');
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
                    <span style={{ fontSize: '13px', color: 'var(--c-mid)' }} className="hide-mob">¿No tienes cuenta?</span>
                    <Link to="/register" className="btn btn-primary btn-sm">Registrarse</Link>
                </div>
            </nav>

            <div className="auth-wrap">
                <div className="auth-side">
                    <div>
                        <img src="/logo.svg" style={{ height: '30px', marginBottom: '24px' }} alt="ServiYa" />
                        <h2 className="auth-side-title">Bienvenido de nuevo a <span>ServiYa</span></h2>
                        {FEATURES.map((f, i) => (
                            <div className="auth-feat" key={i}>
                                <div className="auth-feat-ico"><Icon name={f.icon} size={15} /></div>
                                <div className="auth-feat-txt">{f.text}</div>
                            </div>
                        ))}
                    </div>
                    <div className="auth-side-foot">© 2025 ServiYa · Hecho en Colombia</div>
                </div>

                <div className="auth-form">
                    <div style={{ marginBottom: '28px' }}><img src="/logo.svg" style={{ height: '30px' }} alt="ServiYa" /></div>
                    <div className="auth-title">Iniciar sesión</div>
                    <div className="auth-sub">Selecciona tu rol e ingresa con tus credenciales</div>

                    <div className="role-tabs">
                        {ROLES.map((r) => (
                            <div
                                key={r.id}
                                className={`role-tab ${role === r.id ? 'active' : ''}`}
                                onClick={() => setRole(r.id)}
                            >
                                <Icon name={r.icon} size={18} />
                                {r.label}
                            </div>
                        ))}
                    </div>

                    {error && (
                        <div className="err-box">
                            <Icon name="xCircle" size={15} />
                            {error}
                        </div>
                    )}

                    <div className="input-group">
                        <label className="label">Correo electrónico</label>
                        <div className="input-wrap">
                            <div className="input-ico"><Icon name="mail" size={15} /></div>
                            <input className="input" type="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} />
                        </div>
                    </div>
                    <div className="input-group">
                        <label className="label">Contraseña</label>
                        <div className="input-wrap">
                            <div className="input-ico"><Icon name="lock" size={15} /></div>
                            <input className="input" type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
                        </div>
                    </div>

                    <div className="remember-row" style={{ justifyContent: 'flex-end' }}>
                        <Link className="forgot" to="/recover">¿Olvidaste tu contraseña?</Link>
                    </div>

                    <button className="btn btn-primary btn-full btn-lg" onClick={handleLogin} disabled={loading} style={{ marginBottom: '14px' }}>
                        <Icon name="login" size={17} />
                        {loading ? 'Ingresando…' : 'Iniciar sesión'}
                    </button>
                    <p className="auth-foot">¿No tienes cuenta? <Link to="/register">Regístrate gratis</Link></p>
                </div>
            </div>
        </>
    );
}

export default LoginPage;
