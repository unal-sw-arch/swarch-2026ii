import Link from 'next/link';
import { requireStaff } from '@/lib/auth';
import { can } from '@/lib/permissions';

export const metadata = { title: 'Backoffice' };

type Tile = {
  title: string;
  description: string;
  permission?: string;
};

const TILES: Tile[] = [
  {
    title: 'Pedidos de mostrador',
    description: 'Crear pedidos, cobrar y confirmar pagos.',
    permission: 'orders:create',
  },
  {
    title: 'Reservas',
    description: 'Gestionar las reservas del turno.',
    permission: 'reservations:manage_all',
  },
  {
    title: 'Historial general',
    description: 'Consultar pedidos y cancelaciones de todo el negocio.',
    permission: 'history:read_all',
  },
  {
    title: 'Productos',
    description: 'Crear, editar y retirar productos del catálogo.',
    permission: 'products:manage',
  },
  {
    title: 'Cuentas de empleados',
    description: 'Crear cuentas operativas y desactivarlas.',
    permission: 'users:manage_operational',
  },
  {
    title: 'Roles y permisos',
    description: 'Consultar la matriz y asignar roles.',
    permission: 'roles:manage',
  },
  {
    title: 'Reportes',
    description: 'Ver y exportar reportes del negocio.',
    permission: 'reports:read',
  },
  {
    title: 'Cocina (KDS)',
    description: 'Consultar la cola de preparación.',
    permission: 'kitchen:read',
  },
];

export default function StaffPage() {
  const session = requireStaff();

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
          Solo ves los módulos para los que tienes permiso. La implementación completa llega en
          las siguientes fases del plan; en este PR quedan las rutas preparadas.
        </p>

        <div className="staff-grid">
          {TILES.map((tile) => {
            const allowed = !tile.permission || can(session, tile.permission);
            if (!allowed) return null;

            return (
              <article className="staff-tile" key={tile.title}>
                <h3>{tile.title}</h3>
                <p>{tile.description}</p>
                <span className="badge">Próxima fase</span>
              </article>
            );
          })}
        </div>
      </section>

      <p className="auth-alt">
        <Link href="/">Volver a la portada</Link>
      </p>
    </main>
  );
}
