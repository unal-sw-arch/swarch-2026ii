import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { BackendProfile, UserUpdatePayload } from '../../../types/auth';
import { deleteUser, getUsers, updateUser } from '../../../api/users';
import { UserTable } from './components/UserTable';
import { UserFormModal } from './components/UserFormModal';
import { Pagination } from '../../../shared/components/ui/Pagination';
import { ConfirmDialog } from '../../../shared/components/ui/ConfirmDialog';
import { LoadingSpinner } from '../../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../../shared/components/ui/ErrorAlert';

export function SystemUsersPage(): React.JSX.Element {
  const { token, profile } = useAuth();

  const [users, setUsers] = useState<BackendProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filtros
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  // Modales
  const [modalOpen, setModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<BackendProfile | null>(null);

  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<BackendProfile | null>(null);

  // Paginación
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const fetchUsers = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const params: { search?: string; groups__name?: string } = {};
      if (search.trim()) params.search = search.trim();
      if (roleFilter) params.groups__name = roleFilter;

      const data = await getUsers(params, token);
      setUsers(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar usuarios.');
    } finally {
      setLoading(false);
    }
  }, [token, search, roleFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // Filtro de estado disciplinario (en el cliente, según especificación del backend)
  const filteredUsers = useMemo(() => {
    if (!statusFilter) return users;
    return users.filter((u) => u.status === statusFilter);
  }, [users, statusFilter]);

  const paginatedUsers = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredUsers.slice(start, start + rowsPerPage);
  }, [filteredUsers, page, rowsPerPage]);

  const handleOpenEdit = (user: BackendProfile) => {
    setEditingUser(user);
    setModalOpen(true);
  };

  const handleFormSubmit = async (id: number, payload: UserUpdatePayload) => {
    if (!token) return;
    setSaving(true);
    try {
      await updateUser(id, payload, token);
      await fetchUsers();
    } catch (err) {
      throw err;
    } finally {
      setSaving(false);
    }
  };

  const handleOpenDelete = (user: BackendProfile) => {
    setUserToDelete(user);
    setDeleteConfirmOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!token || !userToDelete) return;
    setSaving(true);
    try {
      await deleteUser(userToDelete.id, token);
      setDeleteConfirmOpen(false);
      setUserToDelete(null);
      await fetchUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al eliminar usuario');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="font-display font-bold text-2xl text-gray-900 dark:text-white">
            Directorio y Roles de Usuarios
          </h1>
          <p className="text-xs text-gray-500">
            Asignación de roles administrativos y gestión de estados disciplinarios del campus UNAL.
          </p>
        </div>
      </div>

      {error && (
        <ErrorAlert
          title="Error en la gestión de usuarios"
          message={error}
          onRetry={fetchUsers}
        />
      )}

      {/* Barra de Filtros (Responsive Grid) */}
      <div className="heroui-card p-4 sm:p-5 rounded-3xl space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 items-center">
          {/* Búsqueda */}
          <div className="relative col-span-1 sm:col-span-2 lg:col-span-1">
            <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-[#00D592]" aria-hidden="true">
              <span className="material-symbols-outlined text-lg">search</span>
            </span>
            <input
              type="search"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Buscar nombre o correo…"
            className="w-full rounded-2xl border border-gray-200/90 bg-white/70 py-2 pr-8 pl-10 text-xs text-gray-900 placeholder-gray-400 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-white transition-all"
          />
          {search && (
            <button
              type="button"
              onClick={() => {
                setSearch('');
                setPage(0);
              }}
              aria-label="Limpiar búsqueda"
              className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            >
              <span className="material-symbols-outlined text-base">close</span>
            </button>
          )}
        </div>

        <select
          value={roleFilter}
          onChange={(e) => {
            setRoleFilter(e.target.value);
            setPage(0);
          }}
          className="rounded-2xl border border-gray-200/90 bg-white/70 px-3 py-2 text-xs text-gray-700 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
        >
          <option value="">Todos los roles…</option>
          <option value="Miembro de la Comunidad">Miembro de la Comunidad</option>
          <option value="Administrador de Eventos">Administrador de Eventos</option>
          <option value="Administrador de Implementos">Administrador de Implementos</option>
          <option value="Administrador del Sistema">Administrador del Sistema</option>
        </select>

        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
          className="rounded-2xl border border-gray-200/90 bg-white/70 px-3 py-2 text-xs text-gray-700 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
        >
          <option value="">Todos los estados…</option>
          <option value="ACT">Activo (ACT)</option>
          <option value="SAN">Sancionado (SAN)</option>
        </select>

          {/* Limpiar (Colspan opcional si sobra espacio) */}
          {(search || roleFilter || statusFilter) && (
            <div className="flex justify-end sm:col-span-2 lg:col-span-1 lg:justify-start">
              <button
                type="button"
                onClick={() => {
                  setSearch('');
                  setRoleFilter('');
                  setStatusFilter('');
                  setPage(0);
                }}
                className="inline-flex items-center gap-1 text-xs font-semibold text-neutral-600 hover:text-neutral-900 dark:text-neutral-300 dark:hover:text-white"
              >
                <span className="material-symbols-outlined text-sm">restart_alt</span>
                Limpiar
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Tabla de Usuarios */}
      {loading ? (
        <div className="py-16">
          <LoadingSpinner label="Consultando usuarios del sistema…" />
        </div>
      ) : (
        <div className="space-y-4">
          <UserTable
            users={paginatedUsers}
            currentUserId={profile?.id}
            onEdit={handleOpenEdit}
            onDelete={handleOpenDelete}
          />

          <Pagination
            count={filteredUsers.length}
            page={page}
            rowsPerPage={rowsPerPage}
            onPageChange={setPage}
            onRowsPerPageChange={(nr) => {
              setRowsPerPage(nr);
              setPage(0);
            }}
          />
        </div>
      )}

      {/* Modal de Asignación de Roles y Estado */}
      <UserFormModal
        open={modalOpen}
        userToEdit={editingUser}
        onClose={() => setModalOpen(false)}
        onSubmit={handleFormSubmit}
        saving={saving}
      />

      {/* Confirmación de Borrado */}
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="¿Eliminar este usuario?"
        description={`Se eliminará permanentemente la cuenta de ${userToDelete?.name} (${userToDelete?.email}).`}
        confirmLabel="Eliminar usuario"
        cancelLabel="Cancelar"
        danger
        pending={saving}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
