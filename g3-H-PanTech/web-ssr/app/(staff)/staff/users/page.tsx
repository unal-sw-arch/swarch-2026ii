import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { assignRole, createUser, removeRole, updateUser } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import { can } from '@/lib/permissions';
import type { Role, UserSummary } from '@/lib/types';

export const metadata = { title: 'Cuentas de empleados' };
export const dynamic = 'force-dynamic';

const OPERATIONAL_ROLES = ['mesero', 'cocinero', 'despachador'];

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function UsersPage({ searchParams }: Props) {
  const session = requirePermission('users:manage_operational');
  const canAssignRoles = can(session, 'roles:manage');

  let users: UserSummary[] = [];
  let roles: Role[] = [];
  let error: string | undefined;
  try {
    users = await apiFetch<UserSummary[]>('/users');
    if (canAssignRoles) roles = await apiFetch<Role[]>('/roles');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar las cuentas.';
  }

  const createRoleOptions = canAssignRoles
    ? roles.map((role) => role.name)
    : OPERATIONAL_ROLES;

  return (
    <main>
      <PageHeader
        eyebrow="Administración"
        title="Cuentas de empleados"
        description="Las cuentas no se eliminan: se desactivan. Un supervisor solo gestiona cuentas operativas; un admin gestiona todas."
        actions={
          <Link className="button button-secondary" href="/staff">
            Volver al panel
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <form action={createUser} className="inline-form card">
        <label>
          Nombre completo
          <input name="full_name" required maxLength={255} placeholder="Ana Pérez" />
        </label>
        <label>
          Correo
          <input name="email" type="email" required maxLength={255} placeholder="ana@panaderia.com" />
        </label>
        <label>
          Contraseña inicial
          <input name="password" type="password" minLength={8} maxLength={72} required />
        </label>
        <label>
          Rol inicial
          <select name="roles" defaultValue={createRoleOptions[0] ?? 'mesero'}>
            {createRoleOptions.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </select>
        </label>
        <button className="button" type="submit">
          Crear cuenta
        </button>
      </form>

      {users.length === 0 ? (
        <EmptyState title="Sin cuentas visibles" description="No hay cuentas que puedas gestionar." />
      ) : (
        <div className="table-wrap card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Correo</th>
                <th>Nombre</th>
                <th>Roles</th>
                <th>Nivel</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.email}</td>
                  <td>{user.full_name}</td>
                  <td>
                    <div className="badge-row">
                      {user.roles.map((role) => (
                        <span className="badge" key={role}>
                          {role}
                          {canAssignRoles && user.roles.length > 1 ? (
                            <form action={removeRole} className="inline-action">
                              <input type="hidden" name="user_id" value={user.id} />
                              <input type="hidden" name="role" value={role} />
                              <button className="inline-x" type="submit" aria-label={`Quitar ${role}`}>
                                ×
                              </button>
                            </form>
                          ) : null}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>{user.level}</td>
                  <td>
                    <span className={user.active ? 'badge badge-ok' : 'badge badge-danger'}>
                      {user.active ? 'Activa' : 'Inactiva'}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <form action={updateUser} className="inline-action">
                        <input type="hidden" name="user_id" value={user.id} />
                        <input type="hidden" name="active" value={user.active ? 'false' : 'true'} />
                        <button className="button button-secondary" type="submit">
                          {user.active ? 'Desactivar' : 'Activar'}
                        </button>
                      </form>
                      {canAssignRoles ? (
                        <form action={assignRole} className="inline-action">
                          <input type="hidden" name="user_id" value={user.id} />
                          <select name="role" defaultValue={roles[0]?.name ?? 'mesero'}>
                            {roles.map((role) => (
                              <option key={role.name} value={role.name}>
                                {role.name}
                              </option>
                            ))}
                          </select>
                          <button className="button button-secondary" type="submit">
                            Asignar rol
                          </button>
                        </form>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
