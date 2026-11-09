import Link from 'next/link';

export default function NotFoundPage() {
  return (
    <main className="center-page">
      <section className="card auth-card">
        <p className="eyebrow">404 · No encontrado</p>
        <h1>La página que buscas no existe.</h1>
        <p className="muted">Puede que la ruta todavía no esté implementada en este entregable.</p>
        <Link className="button" href="/">
          Volver al inicio
        </Link>
      </section>
    </main>
  );
}
