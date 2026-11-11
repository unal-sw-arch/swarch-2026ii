import Link from 'next/link';
import RegisterForm from '@/components/RegisterForm';

export const metadata = { title: 'Crear cuenta' };

export default function RegisterPage() {
  return (
    <main className="center-page">
      <section className="card auth-card">
        <p className="eyebrow">Registro público</p>
        <h1>Crea tu cuenta de cliente</h1>
        <p className="muted">
          El registro público siempre crea una cuenta con rol <strong>cliente</strong>.
          Las cuentas de personal las administra un usuario con permisos de gestión.
        </p>

        <RegisterForm />

        <p className="auth-alt">
          ¿Ya tienes cuenta? <Link href="/login">Ingresa aquí</Link>.
        </p>
      </section>
    </main>
  );
}
