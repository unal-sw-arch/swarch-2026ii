import { useState, useEffect } from "react";
import { Link, useNavigate } from 'react-router-dom';
import { Icon, StatCard, adminApi, adminServiceApi, reportApi, userApi } from '../../../../shared';
import { AdminNavbar } from '../../components/AdminNavbar/AdminNavbar';
import { AdminSidebar } from '../../components/AdminSidebar/AdminSidebar';

import './AdminDashboardPage.css';

const REPORT_TYPE_LABELS = { REQUEST: 'Solicitud', SERVICE_FEEDBACK: 'Reseña servicio', CLIENT_FEEDBACK: 'Reseña cliente' };
const PRIORITY = {
    LOW: { l: 'Baja', b: 'badge-gray' },
    MEDIUM: { l: 'Media', b: 'badge-warn' },
    HIGH: { l: 'Alta', b: 'badge-danger' },
    CRITICAL: { l: 'Crítica', b: 'badge-danger' },
};
const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }) : '—');
const initialsOf = (name) => (name || '?').split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');

export function AdminDashboardPage() {
    const navigate = useNavigate();
    const [stats, setStats] = useState(null);
    const [reports, setReports] = useState([]);
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        Promise.all([
            adminApi.searchUsers({ page: 0, size: 6 }),                 // total + usuarios recientes
            adminApi.searchUsers({ banned: true, page: 0, size: 1 }),   // conteo de baneados (campo real: banned)
            adminServiceApi.search({ available: 'true', page: 0, size: 1 }), // conteo de servicios activos
            reportApi.getAll(0, 5, 'PENDING'),                          // reportes pendientes (top 5 + total)
        ])
            .then(async ([usersPage, bannedPage, servicesPage, reportsPage]) => {
                setUsers(usersPage.content ?? []);
                setStats({
                    totalUsers: usersPage.totalElements ?? 0,
                    banned: bannedPage.totalElements ?? 0,
                    activeServices: servicesPage.totalElements ?? 0,
                    pendingReports: reportsPage.totalElements ?? 0,
                });
                // Resolver nombres de las partes de los reportes (top 5, N+1 aceptable).
                const mapped = await Promise.all((reportsPage.content ?? []).map(async (r) => {
                    const [reporterName, reportedName] = await Promise.all([
                        userApi.getDisplayName(r.reporterId),
                        userApi.getDisplayName(r.reportedUserId),
                    ]);
                    return { ...r, reporterName, reportedName };
                }));
                setReports(mapped);
            })
            .catch(() => { /* dashboard de solo lectura; si algo falla, se muestra vacío */ })
            .finally(() => setLoading(false));
    }, []);

    const statCards = stats ? [
        { icon: 'users', value: String(stats.totalUsers), label: 'Usuarios registrados' },
        { icon: 'wrench', value: String(stats.activeServices), label: 'Servicios activos', variant: 'success' },
        { icon: 'alertTriangle', value: String(stats.pendingReports), label: 'Reportes pendientes', variant: 'danger' },
        { icon: 'ban', value: String(stats.banned), label: 'Usuarios baneados', variant: 'warn' },
    ] : [];

    return (
        <>
            <AdminNavbar />
            <div className="admin-layout-container">
                <AdminSidebar />
                <main className="main-content">
                    <div className="ph"><h1>Panel de administración</h1><p>Gestión y moderación de la plataforma ServiYa</p></div>

                    <div className="g4" style={{ marginBottom: '22px' }}>
                        {loading
                            ? [0, 1, 2, 3].map((i) => <div key={i} className="loading-pulse" style={{ height: 80 }} />)
                            : statCards.map((s) => <StatCard key={s.label} {...s} />)}
                    </div>

                    <div className="sec-title">Reportes pendientes {stats && <span className="badge badge-danger">{stats.pendingReports}</span>}</div>
                    <div className="tbl-wrap" style={{ marginBottom: '14px' }}>
                        <table>
                            <thead><tr><th>Tipo</th><th>Reportante</th><th>Reportado</th><th>Motivo</th><th>Fecha</th><th>Prioridad</th><th></th></tr></thead>
                            <tbody>
                                {!loading && reports.length === 0 && (
                                    <tr><td colSpan="7" style={{ textAlign: 'center', padding: '16px', color: 'var(--c-soft)' }}>Sin reportes pendientes</td></tr>
                                )}
                                {reports.map((r) => {
                                    const pr = PRIORITY[r.priority] || { l: r.priority, b: 'badge-gray' };
                                    return (
                                        <tr key={r.id}>
                                            <td><span className="badge badge-primary">{REPORT_TYPE_LABELS[r.reportType] ?? r.reportType}</span></td>
                                            <td><div className="cell-user"><div className="av av-xs">{initialsOf(r.reporterName)}</div>{r.reporterName}</div></td>
                                            <td><div className="cell-user"><div className="av av-xs">{initialsOf(r.reportedName)}</div>{r.reportedName}</div></td>
                                            <td style={{ color: 'var(--c-mid)', fontSize: '12px' }}>{r.category}</td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{fmtDate(r.createdAt)}</td>
                                            <td><span className={`badge ${pr.b}`}>{pr.l}</span></td>
                                            <td><button className="btn btn-primary btn-sm" onClick={() => navigate('/admin/reports')}><Icon name="search" size={13} />Revisar</button></td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <Link to="/admin/reports" className="btn btn-outline btn-sm" style={{ marginBottom: '22px' }}>Ver todos los reportes →</Link>

                    <div className="sec-title" style={{ marginTop: '22px' }}>Usuarios recientes</div>
                    <div className="tbl-wrap" style={{ marginBottom: '14px' }}>
                        <table>
                            <thead><tr><th>Usuario</th><th>Correo</th><th>Registro</th><th>Estado</th><th></th></tr></thead>
                            <tbody>
                                {!loading && users.length === 0 && (
                                    <tr><td colSpan="5" style={{ textAlign: 'center', padding: '16px', color: 'var(--c-soft)' }}>Sin usuarios</td></tr>
                                )}
                                {users.map((u) => {
                                    const st = u.deletedAt ? { l: 'Eliminado', b: 'badge-gray' } : u.banned ? { l: 'Baneado', b: 'badge-danger' } : { l: 'Activo', b: 'badge-success' };
                                    return (
                                        <tr key={u.id}>
                                            <td>
                                                <div className="cell-user">
                                                    <div className="av av-sm" style={u.banned ? { background: 'var(--c-danger-bg)', color: 'var(--c-danger)' } : undefined}>{initialsOf(u.fullName)}</div>
                                                    <strong>{u.fullName || '—'}</strong>
                                                </div>
                                            </td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{u.email}</td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{fmtDate(u.createdAt)}</td>
                                            <td><span className={`badge ${st.b}`}>{st.l}</span></td>
                                            <td><button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }} onClick={() => navigate('/admin/users')}><Icon name="search" size={13} />Ver</button></td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <Link to="/admin/users" className="btn btn-outline btn-sm">Ver todos los usuarios →</Link>
                </main>
            </div>
        </>
    );
}

export default AdminDashboardPage;
