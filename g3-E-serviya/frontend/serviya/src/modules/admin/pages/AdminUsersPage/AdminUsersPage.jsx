import { useState, useEffect, useCallback } from "react";
import { Icon, Modal, ToastContainer, useToast, adminApi } from '../../../../shared';
import { AdminNavbar } from '../../components/AdminNavbar/AdminNavbar';
import { AdminSidebar } from '../../components/AdminSidebar/AdminSidebar';

import './AdminUsersPage.css';

/** Catalogo de roles gestionables (RF-065/066). ADMIN se concede solo desde aqui, nunca por registro publico. */
const ROLES = [
    { id: 'CLIENT', label: 'Cliente', desc: 'puede contratar servicios' },
    { id: 'OFFERER', label: 'Oferente', desc: 'puede publicar y prestar servicios' },
    { id: 'ADMIN', label: 'Administrador', desc: 'gestiona usuarios, roles y moderación' },
];

const DOCUMENT_TYPES = ['CC', 'CE', 'NIT', 'PASAPORTE'];
const EMPTY_CREATE_USER = { email: '', password: '', fullName: '', role: 'CLIENT', documentType: 'CC', documentNumber: '', phone: '' };

const initialsOf = (name) => (name || '?')
    .split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');

const formatDate = (value) => (value ? new Date(value).toLocaleDateString('es-CO') : '—');

export function AdminUsersPage() {
    const { toasts, showToast } = useToast();

    const [users, setUsers] = useState([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);

    // Filtros (RF-068)
    const [search, setSearch] = useState('');
    const [roleFilter, setRoleFilter] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [page, setPage] = useState(0);

    // Detalle / gestion del usuario seleccionado
    const [selected, setSelected] = useState(null);
    const [selectedRoles, setSelectedRoles] = useState([]);  // [{ roleId, name, assignedAt }]
    const [detail, setDetail] = useState(null);              // GET /admin/users/{id}: perfil + métricas + reportes
    const [mgmtOpen, setMgmtOpen] = useState(false);
    const [editForm, setEditForm] = useState({ email: '', fullName: '', phone: '', description: '' });
    const [saving, setSaving] = useState(false);
    const [deleteOpen, setDeleteOpen] = useState(false);
    const [banOpen, setBanOpen] = useState(false);
    const [banReason, setBanReason] = useState('');
    const [banLoading, setBanLoading] = useState(false);

    // Crear usuario (RF-063)
    const [createOpen, setCreateOpen] = useState(false);
    const [creating, setCreating] = useState(false);
    const [createForm, setCreateForm] = useState(EMPTY_CREATE_USER);

    const loadUsers = useCallback(() => {
        setLoading(true);
        // El backend filtra por email/fullName/role/banned (no por "search"/"status").
        const params = { page, size: 20 };
        if (roleFilter) params.role = roleFilter;
        if (statusFilter === 'BANNED') params.banned = true;
        else if (statusFilter === 'ACTIVE') params.banned = false;
        if (search.trim()) {
            if (search.includes('@')) params.email = search.trim();
            else params.fullName = search.trim();
        }
        adminApi.searchUsers(params)
            .then((data) => {
                // El backend pagina: Page<UserSummaryResponse>
                setUsers(data.content ?? data ?? []);
                setTotal(data.totalElements ?? (data.content ?? data ?? []).length);
            })
            .catch((e) => showToast(e.message || 'No se pudieron cargar los usuarios', 'danger'))
            .finally(() => setLoading(false));
    }, [search, roleFilter, statusFilter, page, showToast]);

    useEffect(() => { loadUsers(); }, [loadUsers]);

    /** RF-067: al abrir el detalle se cargan los roles CON su fecha de concesión. */
    const openUser = (user) => {
        setSelected(user);
        setDetail(null);
        setEditForm({
            email: user.email ?? '',
            fullName: user.fullName ?? '',
            phone: '',
            description: '',
        });
        setMgmtOpen(true);
        adminApi.getUserRoles(user.id)
            .then(setSelectedRoles)
            .catch(() => showToast('No se pudieron cargar los roles', 'danger'));
        // Detalle enriquecido (perfil + métricas + reportes). Precarga el editForm con los valores reales.
        adminApi.getUser(user.id)
            .then((d) => {
                setDetail(d);
                setEditForm((f) => ({
                    ...f,
                    email: d.email ?? f.email,
                    fullName: d.fullName ?? f.fullName,
                    phone: d.phoneNumber ?? '',
                    description: d.bio ?? '',
                }));
            })
            .catch(() => { /* el detalle es complementario; la gestión funciona sin él */ });
    };

    const hasRole = (name) => selectedRoles.some((r) => r.name === name);

    /** RF-065: conceder rol. Es la única vía legítima para otorgar ADMIN. */
    const grantRole = (role) => {
        adminApi.grantRole(selected.id, role)
            .then(() => {
                showToast(`Rol ${role} asignado`, 'success');
                return adminApi.getUserRoles(selected.id).then(setSelectedRoles);
            })
            .then(loadUsers)
            .catch((e) => showToast(e.message || 'No se pudo asignar el rol', 'danger'));
    };

    /** RF-066: retirar rol. Dispara la cascada conservadora en el backend. */
    const revokeRole = (role) => {
        const warning = role === 'OFFERER'
            ? 'Se desactivarán sus servicios y se cancelarán las solicitudes donde actúa como oferente (se notificará a los clientes). ¿Continuar?'
            : role === 'CLIENT'
                ? 'Se cancelarán las solicitudes donde actúa como cliente (se notificará a los oferentes). ¿Continuar?'
                : '¿Retirar el rol de administrador?';
        if (!window.confirm(warning)) return;

        adminApi.revokeRole(selected.id, role)
            .then(() => {
                showToast(`Rol ${role} retirado`, 'success');
                return adminApi.getUserRoles(selected.id).then(setSelectedRoles);
            })
            .then(loadUsers)
            .catch((e) => showToast(e.message || 'No se pudo retirar el rol', 'danger'));
    };

    /** RF-068: edición parcial. El documento no se envía: es inmutable. */
    const saveUser = () => {
        setSaving(true);
        const changes = {};
        const baseEmail = detail?.email ?? selected.email;
        const baseName = detail?.fullName ?? selected.fullName;
        if (editForm.email && editForm.email !== baseEmail) changes.email = editForm.email;
        if (editForm.fullName && editForm.fullName !== baseName) changes.fullName = editForm.fullName;
        if (editForm.phone !== (detail?.phoneNumber ?? '')) changes.phone = editForm.phone;
        if (editForm.description !== (detail?.bio ?? '')) changes.description = editForm.description;

        if (Object.keys(changes).length === 0) {
            showToast('No hay cambios por guardar', 'warn');
            setSaving(false);
            return;
        }
        adminApi.updateUser(selected.id, changes)
            .then(() => {
                showToast('Usuario actualizado', 'success');
                setMgmtOpen(false);
                loadUsers();
            })
            .catch((e) => showToast(e.message || 'No se pudo actualizar', 'danger'))
            .finally(() => setSaving(false));
    };

    /** RF-068: eliminación (soft delete) con la misma cascada que RF-008. */
    const deleteUser = () => {
        adminApi.deleteUser(selected.id)
            .then(() => {
                showToast('Usuario eliminado', 'success');
                setDeleteOpen(false);
                setMgmtOpen(false);
                loadUsers();
            })
            .catch((e) => showToast(e.message || 'No se pudo eliminar', 'danger'));
    };

    const banUser = () => {
        if (!selected) return;
        setBanLoading(true);
        adminApi.banUser(selected.id, { reason: banReason || 'Cuenta suspendida por el administrador' })
            .then(() => {
                showToast('Usuario baneado', 'success');
                setBanOpen(false);
                setBanReason('');
                setMgmtOpen(false);
                loadUsers();
            })
            .catch((e) => showToast(e.message || 'No se pudo banear al usuario', 'danger'))
            .finally(() => setBanLoading(false));
    };

    const unbanUser = () => {
        if (!selected) return;
        setBanLoading(true);
        adminApi.unbanUser(selected.id)
            .then(() => {
                showToast('Usuario desbaneado', 'success');
                setBanOpen(false);
                setBanReason('');
                setMgmtOpen(false);
                loadUsers();
            })
            .catch((e) => showToast(e.message || 'No se pudo desbanear al usuario', 'danger'))
            .finally(() => setBanLoading(false));
    };

    /** RF-063: alta de usuario por el admin. role obligatorio; documento/telefono opcionales. */
    const createUser = () => {
        if (!createForm.email || !createForm.password || !createForm.fullName) {
            showToast('Correo, contraseña y nombre son obligatorios', 'danger');
            return;
        }
        if (createForm.password.length < 8) {
            showToast('La contraseña debe tener al menos 8 caracteres', 'danger');
            return;
        }
        const payload = { ...createForm };
        // Documento va completo o no va; teléfono opcional.
        if (!payload.documentNumber?.trim()) { delete payload.documentType; delete payload.documentNumber; }
        if (!payload.phone?.trim()) delete payload.phone;

        setCreating(true);
        adminApi.createUser(payload)
            .then(() => {
                showToast('Usuario creado', 'success');
                setCreateOpen(false);
                setCreateForm(EMPTY_CREATE_USER);
                loadUsers();
            })
            .catch((e) => showToast(e.message || 'No se pudo crear el usuario', 'danger'))
            .finally(() => setCreating(false));
    };

    const statusOf = (u) => {
        if (u.deletedAt) return { label: 'Eliminado', badge: 'badge-gray' };
        if (u.banned) return { label: 'Baneado', badge: 'badge-danger' };
        return { label: 'Activo', badge: 'badge-success' };
    };

    return (
        <>
            <AdminNavbar />
            <div className="admin-layout-container">
                <AdminSidebar />
                <main className="main-content">
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '10px' }}>
                        <div className="ph" style={{ margin: 0 }}>
                            <h1>Gestión de usuarios</h1>
                            <p>{loading ? 'Cargando…' : `${total} usuario(s) encontrados`}</p>
                        </div>
                        <button className="btn btn-primary" onClick={() => { setCreateForm(EMPTY_CREATE_USER); setCreateOpen(true); }}>
                            <Icon name="plus" size={15} />Crear usuario
                        </button>
                    </div>

                    <div className="users-filters">
                        <div className="input-wrap" style={{ flex: 1, minWidth: '200px' }}>
                            <div className="input-ico"><Icon name="search" size={15} /></div>
                            <input className="input" placeholder="Buscar por nombre o correo..."
                                value={search}
                                onChange={(e) => { setPage(0); setSearch(e.target.value); }} />
                        </div>
                        <select className="input" style={{ width: 'auto', padding: '9px 12px', fontSize: '13px' }}
                            value={roleFilter} onChange={(e) => { setPage(0); setRoleFilter(e.target.value); }}>
                            <option value="">Todos los roles</option>
                            {ROLES.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
                        </select>
                        <select className="input" style={{ width: 'auto', padding: '9px 12px', fontSize: '13px' }}
                            value={statusFilter} onChange={(e) => { setPage(0); setStatusFilter(e.target.value); }}>
                            <option value="">Todos los estados</option>
                            <option value="ACTIVE">Activos</option>
                            <option value="BANNED">Baneados</option>
                        </select>
                    </div>

                    <div className="tbl-wrap">
                        <table>
                            <thead><tr><th>Usuario</th><th>Correo</th><th>Registro</th><th>Estado</th><th>Acciones</th></tr></thead>
                            <tbody>
                                {!loading && users.length === 0 && (
                                    <tr><td colSpan="5" style={{ textAlign: 'center', padding: '20px', color: 'var(--c-mid)' }}>Sin resultados</td></tr>
                                )}
                                {users.map((u) => {
                                    const st = statusOf(u);
                                    return (
                                        <tr key={u.id}>
                                            <td>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '9px' }}>
                                                    <div className="av av-sm" style={u.banned ? { background: 'var(--c-danger-bg)', color: 'var(--c-danger)' } : undefined}>{initialsOf(u.fullName)}</div>
                                                    <div>
                                                        <div style={{ fontWeight: 700, fontSize: '13px' }}>{u.fullName || '—'}</div>
                                                        <div style={{ fontSize: '11px', color: 'var(--c-soft)' }}>ID: {u.id}</div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{u.email}</td>
                                            <td style={{ fontSize: '12px', color: 'var(--c-soft)' }}>{formatDate(u.createdAt)}</td>
                                            <td><span className={`badge ${st.badge}`}>{st.label}</span></td>
                                            <td>
                                                <div style={{ display: 'flex', gap: '5px' }}>
                                                    <button className="btn btn-ghost btn-sm" style={{ border: '1px solid var(--c-border)' }}
                                                        onClick={() => openUser(u)} title="Gestionar usuario y roles">
                                                        <Icon name="search" size={13} />
                                                    </button>
                                                    {!u.banned ? (
                                                        <button className="btn btn-danger btn-sm"
                                                            onClick={() => { setSelected(u); setBanReason(''); setBanOpen(true); }} title="Banear">
                                                            <Icon name="ban" size={13} />
                                                        </button>
                                                    ) : (
                                                        <button className="btn btn-ghost btn-sm"
                                                            onClick={() => { setSelected(u); setBanReason(''); setBanOpen(true); }} title="Desbanear">
                                                            <Icon name="check" size={13} />
                                                        </button>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', marginTop: '18px', gap: '8px' }}>
                        <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</button>
                        <span style={{ fontSize: '12px', color: 'var(--c-mid)' }}>Página {page + 1}</span>
                        <button className="btn btn-ghost btn-sm" disabled={users.length < 20} onClick={() => setPage((p) => p + 1)}>Siguiente</button>
                    </div>
                </main>
            </div>

            {/* Detalle del usuario: perfil (RF-068) + roles (RF-065/066/067) */}
            <Modal open={mgmtOpen} onClose={() => setMgmtOpen(false)}>
                <div className="modal-title">Gestionar usuario</div>
                <div className="user-head">
                    <div className="av av-md">{initialsOf(selected?.fullName)}</div>
                    <div>
                        <div style={{ fontWeight: 700 }}>{selected?.fullName || '—'}</div>
                        <div style={{ fontSize: '12px', color: 'var(--c-mid)' }}>{selected?.email}</div>
                    </div>
                </div>

                {detail && (
                    <div style={{ background: 'var(--c-bg-s)', borderRadius: 'var(--r-lg)', padding: '10px 12px', marginBottom: '12px', fontSize: '12px', color: 'var(--c-mid)' }}>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 12px' }}>
                            <div><strong>Registrado:</strong> {formatDate(detail.createdAt)}</div>
                            <div><strong>Estado:</strong> {detail.deletedAt ? 'Eliminado' : detail.banned ? 'Baneado' : 'Activo'}</div>
                            <div><strong>Documento:</strong> {detail.documentType || '—'} {detail.documentNumber || ''}</div>
                            <div><strong>Reportes:</strong> {detail.reportsReceived ?? 0} recib. / {detail.reportsSent ?? 0} env.</div>
                        </div>
                        {detail.bio && <div style={{ marginTop: '6px' }}><strong>Bio:</strong> {detail.bio}</div>}
                        {detail.clientMetrics && (
                            <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--c-border)' }}>
                                <strong>Como cliente:</strong> ⭐ {Number(detail.clientMetrics.averageRating ?? 0).toFixed(1)}
                                {' · '}{detail.clientMetrics.totalCompletedRequests ?? 0} completadas
                                {' · '}{detail.clientMetrics.totalCancelledRequests ?? 0} canceladas
                                {' · '}{detail.clientMetrics.activeRequests ?? 0} activas
                            </div>
                        )}
                        {detail.offererMetrics && (
                            <div style={{ marginTop: '6px', paddingTop: '8px', borderTop: '1px solid var(--c-border)' }}>
                                <strong>Como oferente:</strong> ⭐ {Number(detail.offererMetrics.averageRating ?? 0).toFixed(1)}
                                {' · '}{detail.offererMetrics.totalCompletedServices ?? 0} servicios
                                {' · '}{detail.offererMetrics.totalCancelledServices ?? 0} cancelados
                                {' · '}{detail.offererMetrics.totalRequestsReceived ?? 0} recibidas
                            </div>
                        )}
                    </div>
                )}

                <div className="input-group">
                    <label className="label">Roles asignados (RF-065/066/067)</label>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '9px' }}>
                        {ROLES.map((r) => {
                            const assignment = selectedRoles.find((sr) => sr.name === r.id);
                            return (
                                <div key={r.id} className="ck-row" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <div style={{ flex: 1 }}>
                                        <strong>{r.label}</strong> — {r.desc}
                                        {assignment && (
                                            <div style={{ fontSize: '11px', color: 'var(--c-soft)' }}>
                                                Asignado el {formatDate(assignment.assignedAt)}
                                            </div>
                                        )}
                                    </div>
                                    {hasRole(r.id)
                                        ? <button className="btn btn-danger btn-sm" onClick={() => revokeRole(r.id)}>Quitar</button>
                                        : <button className="btn btn-primary btn-sm" onClick={() => grantRole(r.id)}>Asignar</button>}
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div className="input-group">
                    <label className="label">Editar datos (RF-068)</label>
                    <input className="input" placeholder="Nombre completo" value={editForm.fullName}
                        onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })} />
                </div>
                <div className="input-group">
                    <input className="input" type="email" placeholder="Correo" value={editForm.email}
                        onChange={(e) => setEditForm({ ...editForm, email: e.target.value })} />
                </div>
                <div className="input-group">
                    <input className="input" placeholder="Teléfono (se cifra AES-256-GCM)" value={editForm.phone}
                        onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })} />
                </div>
                <div className="note-box" style={{ marginBottom: '12px' }}>
                    El <strong>tipo y número de documento no son editables</strong>: se fijan al registrarse y son inmutables incluso para un administrador.
                </div>

                <div style={{ display: 'flex', gap: '7px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setMgmtOpen(false)}>Cerrar</button>
                    <button className="btn btn-primary btn-full" onClick={saveUser} disabled={saving}>
                        {saving ? 'Guardando…' : 'Guardar cambios'}
                    </button>
                </div>
                <button className="btn btn-danger btn-full" style={{ marginTop: '8px' }} onClick={() => setDeleteOpen(true)}>
                    <Icon name="trash" size={15} />Eliminar usuario
                </button>
            </Modal>

            <Modal open={deleteOpen} onClose={() => setDeleteOpen(false)}>
                <div className="modal-title" style={{ color: 'var(--c-danger)' }}>Eliminar usuario</div>
                <div className="modal-sub">
                    Se marcará la cuenta como eliminada (no se borra físicamente). Sus servicios se desactivarán
                    y sus solicitudes pendientes/aceptadas se cancelarán, notificando a las contrapartes.
                </div>
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setDeleteOpen(false)}>Cancelar</button>
                    <button className="btn btn-danger btn-full" onClick={deleteUser}>Eliminar definitivamente</button>
                </div>
            </Modal>

            <Modal open={banOpen} onClose={() => setBanOpen(false)}>
                <div className="modal-title">{selected?.banned ? 'Desbanear usuario (RF-070)' : 'Banear usuario (RF-069)'}</div>
                <div className="modal-sub">{selected?.banned ? 'La cuenta volverá a poder acceder a la plataforma.' : 'El usuario quedará bloqueado y no podrá usar la plataforma.'}</div>
                {!selected?.banned && (
                    <div className="input-group">
                        <label className="label">Motivo del baneo</label>
                        <textarea className="input" rows="3" value={banReason} onChange={(e) => setBanReason(e.target.value)} placeholder="Explica la razón de la suspensión" />
                    </div>
                )}
                <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setBanOpen(false)}>Cancelar</button>
                    <button className="btn btn-danger btn-full" disabled={banLoading} onClick={selected?.banned ? unbanUser : banUser}>
                        {selected?.banned ? <><Icon name="check" size={15} />Confirmar desbaneo</> : <><Icon name="ban" size={15} />Confirmar baneo</>}
                    </button>
                </div>
            </Modal>

            {/* Crear usuario (RF-063) */}
            <Modal open={createOpen} onClose={() => setCreateOpen(false)}>
                <div className="modal-title">Crear usuario</div>
                <div className="modal-sub">El usuario podrá iniciar sesión con el correo y la contraseña que definas.</div>

                <div className="input-group">
                    <label className="label">Nombre completo *</label>
                    <input className="input" value={createForm.fullName} onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })} placeholder="Nombre y apellido" />
                </div>
                <div className="input-group">
                    <label className="label">Correo *</label>
                    <input className="input" type="email" value={createForm.email} onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })} placeholder="correo@ejemplo.com" />
                </div>
                <div className="input-group">
                    <label className="label">Contraseña * (mín. 8)</label>
                    <input className="input" type="password" value={createForm.password} onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })} placeholder="••••••••" />
                </div>
                <div className="input-group">
                    <label className="label">Rol inicial *</label>
                    <select className="input" value={createForm.role} onChange={(e) => setCreateForm({ ...createForm, role: e.target.value })}>
                        {ROLES.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
                    </select>
                </div>

                <div style={{ display: 'flex', gap: '8px' }}>
                    <div className="input-group" style={{ flex: '0 0 40%' }}>
                        <label className="label">Tipo doc.</label>
                        <select className="input" value={createForm.documentType} onChange={(e) => setCreateForm({ ...createForm, documentType: e.target.value })}>
                            {DOCUMENT_TYPES.map((d) => <option key={d} value={d}>{d}</option>)}
                        </select>
                    </div>
                    <div className="input-group" style={{ flex: 1 }}>
                        <label className="label">Número de documento</label>
                        <input className="input" value={createForm.documentNumber} onChange={(e) => setCreateForm({ ...createForm, documentNumber: e.target.value })} placeholder="Opcional" />
                    </div>
                </div>
                <div className="input-group">
                    <label className="label">Teléfono</label>
                    <input className="input" value={createForm.phone} onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })} placeholder="Opcional (se cifra AES-256-GCM)" />
                </div>

                <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                    <button className="btn btn-ghost btn-full" onClick={() => setCreateOpen(false)}>Cancelar</button>
                    <button className="btn btn-primary btn-full" onClick={createUser} disabled={creating}>
                        {creating ? 'Creando…' : 'Crear usuario'}
                    </button>
                </div>
            </Modal>

            <ToastContainer toasts={toasts} />
        </>
    );
}

export default AdminUsersPage;
