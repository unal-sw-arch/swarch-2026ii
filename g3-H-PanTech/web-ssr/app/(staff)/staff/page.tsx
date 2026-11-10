import Link from 'next/link';
import { requireStaff } from '@/lib/auth';
import { can, canAny } from '@/lib/permissions';

export const metadata = { title: 'Backoffice' };
export const dynamic = 'force-dynamic';

type Tile = {
  title: string;
  description: string;
  href: string;
  permission?: string | string[];
};

const TILES: Tile[] = [
  {
    title: 'Pedidos de mostrador',
    description: 'Crear pedidos, cobrar y confirmar pagos.',
    href: '/staff/orders',
    permission: ['orders:create', 'orders:charge'],
  },
  {
    title: 'Reservas',
    description: 'Confirmar o cancelar las reservas del turno.',
    href: '/staff/reservations',
    permission: 'reservations:manage_all',
  },
  {
    title: 'Historial general',
    description: 'Consultar pedidos pagados y su línea de tiempo.',
    href: '/staff/history',
    permission: 'history:read_all',
  },
  {
    title: 'Cancelaciones',
    description: 'Cancelar pedidos que aún no han sido pagados.',
    href: '/staff/cancel',
    permission: 'orders:read_all',
  },
  {
    title: 'Productos',
    description: 'Crear, editar y retirar productos del catálogo.',
    href: '/staff/products',
    permission: 'products:manage',
  },
  {
    title: 'Cuentas de empleados',
    description: 'Crear cuentas operativas, desactivarlas y asignar roles.',
    href: '/staff/users',
    permission: 'users:manage_operational',
  },
  {
    title: 'Roles y permisos',
    description: 'Consultar la matriz de autorización y asignar roles.',
    href: '/staff/roles',
    permission: 'roles:manage',
  },
  {
    title: 'Reportes',
    description: 'Ver ventas, tiempos y exportar CSV/PDF.',
    href: '/staff/reports',
    permission: 'reports:read',
  },
  {
    title: 'Cocina (KDS)',
    description: 'Preparar y despachar pedidos según tus permisos.',
    href: '/staff/kitchen',
    permission: 'kitchen:read',
  },
];

export default function StaffPage() {
  const session = requireStaff();
  const visible = TILES.filter((tile) => {
    if (!tile.permission) return true;
    return Array.isArray(tile.permission)
      ? canAny(session, tile.permission)
      : can(session, tile.permission);
  });

  return (
    <main>
      <header className="staff-header">
        <div>
          <p className="eyebrow">Backoffice</p>
          <h1>Hola, {session.email}</h1>
          <p className="muted">
            Esta vista se renderiza en el servidor con la sesión de tu cookie. Los accesos se
            filtran por tus permisos efectivos; el backend vuelve a validarlos en cada operación.
          </p>
        </div>
        <div className="badge-row">
          <span className="badge badge-accent">Nivel {session.level}</span>
          {session.roles.map((role) => (
            <span className="badge" key={role}>
              {role}
            </span>
          ))}
        </div>
      </header>

      <section className="card" aria-labelledby="permissions-title">
        <h2 id="permissions-title">Permisos efectivos</h2>
        <p className="muted">
          Unión de los permisos de todos tus roles. El nivel mostrado es el máximo.
        </p>
        <ul className="permission-list">
          {session.permissions.length > 0 ? (
            session.permissions.map((permission) => <li key={permission}>{permission}</li>)
          ) : (
            <li>Sin permisos asignados</li>
          )}
        </ul>
      </section>

      <section style={{ marginTop: '1.5rem' }} aria-labelledby="modules-title">
        <h2 id="modules-title">Módulos del backoffice</h2>
        <p className="muted">
          Solo ves los módulos para los que tienes permiso. Las operaciones se validan nuevamente
          en el API Gateway y en cada servicio.
        </p>

        <div className="staff-grid">
          {visible.map((tile) => (
            <article className="staff-tile" key={tile.title}>
              <h3>{tile.title}</h3>
              <p>{tile.description}</p>
              <Link className="button button-secondary" href={tile.href}>
                Abrir módulo
              </Link>
            </article>
          ))}
        </div>
      </section>

      <p className="auth-alt">
        <Link href="/">Volver a la portada</Link>
      </p>
    </main>
  );
}
