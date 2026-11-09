import Link from 'next/link';

export const metadata = { title: 'Acceso denegado' };

export default function ForbiddenPage() {
  return (
    <main className="center-page">
      <section className="card auth-card">
        <p className="eyebrow">403 · Acceso denegado</p>
        <h1>No tienes permiso para esta vista.</h1>
        <p className="muted">
          Tu cuenta está autenticada, pero el rol o permiso no alcanza para esta sección.
          Si crees que es un error, pide a un administrador que revise tus roles.
        </p>
        <div className="hero-actions">
          <Link className="button" href="/">
            Volver al inicio
          </Link>
          <Link className="button button-secondary" href="/login">
            Cambiar de cuenta
          </Link>
        </div>
      </section>
    </main>
  );
}
