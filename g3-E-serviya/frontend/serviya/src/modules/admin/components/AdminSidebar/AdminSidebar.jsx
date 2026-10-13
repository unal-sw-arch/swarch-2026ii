import { Link, useLocation, useNavigate } from 'react-router-dom';
import { clearToken } from '../../../../shared/api';
import { useSidebarOpen, sidebarStore } from '../../../../shared/hooks/useSidebar';
import './AdminSidebar.css';

const ITEMS = [
    { to: '/admin/dashboard', label: 'Panel', svg: <><rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /></> },
    { to: '/admin/users', label: 'Usuarios', svg: <><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /></> },
    { to: '/admin/reports', label: 'Reportes', svg: <><path d="m10.29 3.86-8.5 14.72A1 1 0 0 0 2.68 20h16.64a1 1 0 0 0 .89-1.42l-8.5-14.72a1 1 0 0 0-1.76 0z" /><path d="M12 9v4m0 4h.01" /></> },
    { to: '/admin/services', label: 'Servicios', svg: <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" /> },
];

export function AdminSidebar() {
    const location = useLocation();
    const navigate = useNavigate();
    const open = useSidebarOpen();

    const handleLogout = () => {
        sidebarStore.close();
        clearToken();          // invalida la sesión: elimina el JWT de localStorage
        navigate('/login');
    };

    return (
        <>
            {open && <div className="sidebar-backdrop" onClick={() => sidebarStore.close()} />}
            <aside className={`sidebar ${open ? 'open' : ''}`}>
                <div className="sb-sep">Administración</div>

                {ITEMS.map((it) => (
                    <Link
                        key={it.to}
                        to={it.to}
                        onClick={() => sidebarStore.close()}
                        className={`sb-item ${location.pathname === it.to ? 'active' : ''}`}
                    >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">{it.svg}</svg>
                        {it.label}
                    </Link>
                ))}

                <div className="sb-spacer"></div>

                <button type="button" className="sb-item sb-danger" onClick={handleLogout}
                    style={{ width: '100%', textAlign: 'left', background: 'none', border: 'none', font: 'inherit', cursor: 'pointer' }}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                    </svg>
                    Cerrar sesión
                </button>
            </aside>
        </>
    );
}
