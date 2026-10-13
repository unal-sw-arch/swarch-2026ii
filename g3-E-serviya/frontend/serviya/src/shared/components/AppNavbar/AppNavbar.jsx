import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '../Icon/Icon';
import { Avatar } from '../Avatar/Avatar';
import { sidebarStore } from '../../hooks/useSidebar';
import { profileApi, getApiImageUrl, isAuthenticated } from '../../api';

/** Iniciales (máx. 2) a partir del nombre completo, para el avatar de texto. */
function initialsOf(name) {
    if (!name) return null;
    return name.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}

export function AppNavbar({ avatar = 'JP', avatarSrc: controlledAvatarSrc = null, links = [], showBell = true, unreadCount = 0, showBurger = false }) {
    // Barra según sesión: si no hay usuario logueado se muestran login/registro (esta barra se usa
    // también en páginas públicas como el detalle de servicio o el perfil del oferente).
    const authed = isAuthenticated();

    const [fetchedAvatarSrc, setFetchedAvatarSrc] = useState(null);
    const [resolvedInitials, setResolvedInitials] = useState(avatar);

    useEffect(() => {
        if (!isAuthenticated()) return;   // sin sesión no se pide el perfil (evita 401)
        let cancelled = false;
        profileApi.getMyProfile()
            .then((profile) => {
                if (cancelled) return;
                const initials = initialsOf(profile?.fullName || profile?.name);
                if (initials) setResolvedInitials(initials);
                setFetchedAvatarSrc(getApiImageUrl(profile?.profilePhotoUrl || null));
            })
            .catch(() => {});

        return () => {
            cancelled = true;
        };
    }, []);

    // El src controlado (p. ej. tras subir una foto en el perfil) tiene prioridad; si no,
    // se usa la foto del perfil recién cargada.
    const resolvedAvatarSrc = getApiImageUrl(controlledAvatarSrc) || fetchedAvatarSrc;

    return (
        <nav className="nav">
            {showBurger && (
                <button className="nav-burger" onClick={() => sidebarStore.toggle()} aria-label="Abrir menú">
                    <Icon name="menu" size={20} />
                </button>
            )}
            <Link to="/" className="nav-logo">
                <img src="/logo.svg" alt="ServiYa" style={{ height: '24px' }} />
            </Link>
            <div className="nav-spacer" />
            {links.length > 0 && (
                <div className="nav-links">
                    {links.map((l) => (
                        <Link key={l.to} to={l.to}>{l.label}</Link>
                    ))}
                </div>
            )}
            <div className="nav-actions">
                {authed ? (
                    <>
                        {showBell && (
                            <Link to="/notifications" className="nav-icon" aria-label="Notificaciones">
                                <Icon name="bell" size={16} />
                                {unreadCount > 0 && <span className="nav-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
                            </Link>
                        )}
                        <Link to="/profile" className="nav-av">
                            <Avatar src={resolvedAvatarSrc} initials={resolvedInitials} />
                        </Link>
                    </>
                ) : (
                    <>
                        <Link to="/login" className="btn btn-outline btn-sm">Iniciar sesión</Link>
                        <Link to="/register" className="btn btn-primary btn-sm">Registrarse</Link>
                    </>
                )}
            </div>
        </nav>
    );
}
