import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { profileApi } from '../../../../shared/api';
import { Icon } from '../../../../shared';
import { sidebarStore } from '../../../../shared/hooks/useSidebar';
import './AdminNavbar.css';

/** Iniciales (máx. 2) a partir del nombre completo. */
function initialsOf(name) {
    if (!name) return null;
    return name.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}

export function AdminNavbar() {
    const [initials, setInitials] = useState('AD');

    useEffect(() => {
        let cancelled = false;
        profileApi.getMyProfile()
            .then((profile) => {
                if (cancelled) return;
                const ini = initialsOf(profile?.fullName || profile?.name);
                if (ini) setInitials(ini);
            })
            .catch(() => {});
        return () => { cancelled = true; };
    }, []);

    return (
        <nav className="admin-nav">
            <button className="nav-burger" onClick={() => sidebarStore.toggle()} aria-label="Abrir menú">
                <Icon name="menu" size={20} />
            </button>
            <Link
                to="/"
                className="nav-logo">
                <img
                    src="/logo.svg"
                    alt="ServiYa"
                    style={{ height: '24px' }} />
            </Link>
            <div className="nav-spacer"></div>
            <div style={{ display: 'flex', alignItems: 'center' }}>
                <span className="admin-badge">ADMIN</span>
            </div>
            <div className="nav-actions">
                <div className="nav-av" style={{ background: '#0F172A' }}>{initials}</div>
            </div>
        </nav>
    );
}