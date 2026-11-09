import Link from 'next/link';
import LoginForm from '@/components/LoginForm';

export const metadata = { title: 'Ingresar' };

export default function LoginPage({
  searchParams,
}: {
  searchParams: { next?: string };
}) {
  return (
    <main className="center-page">
      <section className="card auth-card">
        <p className="eyebrow">Sesión</p>
        <h1>Ingresa a tu cuenta</h1>
        <p className="muted">
          Usa tu correo y contraseña. La sesión se guarda en una cookie
          <code> httpOnly</code>; nunca escribimos tokens en <code>localStorage</code>.
        </p>

        <LoginForm next={searchParams.next} />

        <p className="auth-alt">
          ¿Todavía no tienes cuenta? <Link href="/register">Regístrate como cliente</Link>.
        </p>

        <details className="auth-alt">
          <summary>Cuentas de demostración</summary>
          <div className="demo-grid">
            <span className="demo-user">
              <strong>cliente@demo.com</strong>
              <span>cliente</span>
            </span>
            <span className="demo-user">
              <strong>mesero@demo.com</strong>
              <span>mesero</span>
            </span>
            <span className="demo-user">
              <strong>supervisor@demo.com</strong>
              <span>supervisor</span>
            </span>
            <span className="demo-user">
              <strong>admin@demo.com</strong>
              <span>admin</span>
            </span>
            <span className="demo-user">
              <strong>multi@demo.com</strong>
              <span>mesero + despachador</span>
            </span>
          </div>
          <p className="muted" style={{ marginBottom: 0 }}>
            Contraseña para todas: <code>Demo1234!</code>
          </p>
        </details>
      </section>
    </main>
  );
}
