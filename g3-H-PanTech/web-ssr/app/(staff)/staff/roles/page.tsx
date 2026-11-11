import Link from 'next/link';
import Alert from '@/components/Alert';
import PageHeader from '@/components/PageHeader';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import { formatDateTime } from '@/lib/format';
import type { AuditLog, Permission, Role } from '@/lib/types';

export const metadata = { title: 'Roles y permisos' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function RolesPage({ searchParams }: Props) {
  requirePermission('roles:manage');

  let roles: Role[] = [];
  let permissions: Permission[] = [];
  let audit: AuditLog[] = [];
  let error: string | undefined;
  let auditError: string | undefined;
  try {
    [roles, permissions] = await Promise.all([
      apiFetch<Role[]>('/roles'),
      apiFetch<Permission[]>('/permissions'),
    ]);
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar la matriz de permisos.';
  }
  try {
    if (!error) audit = await apiFetch<AuditLog[]>('/audit?limit=25');
  } catch (caught) {
    auditError = caught instanceof ApiError ? caught.message : 'No se pudo cargar la auditoría.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Administración"
        title="Roles y permisos"
        description="Los permisos efectivos son la unión de los roles; el nivel efectivo es el máximo. La asignación se hace desde Cuentas de empleados."
        actions={
          <>
            <Link className="button button-secondary" href="/staff/users">
              Gestionar cuentas
            </Link>
            <Link className="button button-secondary" href="/staff">
              Volver al panel
            </Link>
          </>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <div className="table-wrap card">
        <table className="data-table">
          <thead>
            <tr>
              <th>Rol</th>
              <th>Nivel</th>
              <th>Personal</th>
              <th>Descripción</th>
              <th>Permisos</th>
            </tr>
          </thead>
          <tbody>
            {roles.map((role) => (
              <tr key={role.id}>
                <td>
                  <strong>{role.name}</strong>
                </td>
                <td>{role.level}</td>
                <td>{role.is_staff ? 'Sí' : 'No'}</td>
                <td>{role.description}</td>
                <td>
                  <div className="badge-row">
                    {role.permissions.map((permission) => (
                      <span className="badge" key={permission}>
                        {permission}
                      </span>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="card" style={{ marginTop: '1.5rem' }}>
        <h2>Catálogo de permisos</h2>
        <ul className="permission-list">
          {permissions.map((permission) => (
            <li key={permission.code} title={permission.description}>
              {permission.code}
            </li>
          ))}
        </ul>
      </section>

      <section className="card" style={{ marginTop: '1.5rem' }}>
        <h2>Auditoría reciente</h2>
        {auditError ? <p className="form-error">{auditError}</p> : null}
        {audit.length === 0 ? (
          <p className="muted">Sin eventos de auditoría todavía.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Acción</th>
                  <th>Entidad</th>
                  <th>Actor</th>
                  <th>Fecha</th>
                </tr>
              </thead>
              <tbody>
                {audit.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.action}</td>
                    <td>
                      {entry.entity}
                      {entry.entity_id ? ` · ${entry.entity_id.slice(0, 8)}` : ''}
                    </td>
                    <td>{entry.actor_id?.slice(0, 8) ?? 'sistema'}</td>
                    <td>{formatDateTime(entry.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
