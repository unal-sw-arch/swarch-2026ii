import Link from 'next/link';
import { getSession } from '@/lib/auth';
import { isStaff } from '@/lib/permissions';

export default function HomePage() {
  const session = getSession();

  return (
    <main>
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">Panadería Express · Prototipo 2</p>
          <h1>Bienvenido a la panadería que respeta tu tiempo.</h1>
          <p className="lead">
            Consulta el menú, pide para llevar y sigue el estado de tu pedido. El personal
            administra la operación desde el backoffice con roles y permisos.
          </p>
          <div className="hero-actions">
            {session ? (
              <>
                <Link className="button" href={isStaff(session) ? '/staff' : '/'}>
                  {isStaff(session) ? 'Ir al backoffice' : 'Ver el menú'}
                </Link>
                <Link className="button button-secondary" href="/">
                  Mi cuenta
                </Link>
              </>
            ) : (
              <>
                <Link className="button" href="/login">
                  Ingresar
                </Link>
                <Link className="button button-secondary" href="/register">
                  Crear cuenta de cliente
                </Link>
              </>
            )}
          </div>
        </div>

        <div className="hero-card" aria-label="Resumen del servicio">
          <span className="hero-badge">SSR</span>
          <h2>Renderizado en el servidor</h2>
          <p>
            La Web SSR habla con el API Gateway y guarda la sesión en cookies
            <code> httpOnly</code>. El token nunca llega a <code>localStorage</code>.
          </p>
          <ul>
            <li>Login y registro público</li>
            <li>Roles, permisos y jerarquía</li>
            <li>Rutas protegidas por middleware</li>
          </ul>
        </div>
      </section>

      <section className="feature-grid" aria-label="Funciones principales">
        <article className="card">
          <span className="card-icon" aria-hidden="true">🧾</span>
          <h2>Pedidos para llevar</h2>
          <p>El cliente arma su pedido y paga de forma simulada; el total se calcula en el servidor.</p>
        </article>
        <article className="card">
          <span className="card-icon" aria-hidden="true">📅</span>
          <h2>Reservas propias</h2>
          <p>Cada cliente crea, consulta y cancela únicamente sus reservas.</p>
        </article>
        <article className="card">
          <span className="card-icon" aria-hidden="true">🔐</span>
          <h2>Acceso por permisos</h2>
          <p>La interfaz oculta lo que no corresponde, pero el backend vuelve a validar cada acción.</p>
        </article>
      </section>

      <section className="notice">
        <strong>Estado del prototipo:</strong> este primer entregable incluye Identity Service,
        login, registro y las rutas protegidas. La tienda y el backoffice llegan en las
        siguientes fases del plan.
      </section>
    </main>
  );
}
