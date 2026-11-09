'use client';

import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

type ErrorBody = {
  code?: string;
  message?: string;
  details?: Record<string, string>;
};

export default function RegisterForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const data = new FormData(event.currentTarget);
    const fullName = String(data.get('full_name') ?? '').trim();
    const email = String(data.get('email') ?? '').trim();
    const password = String(data.get('password') ?? '');
    const confirm = String(data.get('confirm') ?? '');

    if (password.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (password !== confirm) {
      setError('Las contraseñas no coinciden.');
      return;
    }

    setLoading(true);

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ full_name: fullName, email, password }),
      });

      const body = (await response.json().catch(() => null)) as ErrorBody | null;

      if (!response.ok) {
        const detail = body?.details ? Object.values(body.details)[0] : undefined;
        setError(detail ?? body?.message ?? 'No se pudo crear la cuenta.');
        return;
      }

      router.push('/');
      router.refresh();
    } catch {
      setError('No se pudo contactar al servidor. Intenta de nuevo.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}

      <div className="field">
        <label htmlFor="register-name">Nombre completo</label>
        <input
          id="register-name"
          name="full_name"
          type="text"
          autoComplete="name"
          placeholder="Ana Pérez"
          maxLength={255}
          required
          disabled={loading}
        />
      </div>

      <div className="field">
        <label htmlFor="register-email">Correo electrónico</label>
        <input
          id="register-email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="ana@correo.com"
          maxLength={255}
          required
          disabled={loading}
        />
      </div>

      <div className="field">
        <label htmlFor="register-password">Contraseña</label>
        <input
          id="register-password"
          name="password"
          type="password"
          autoComplete="new-password"
          placeholder="Mínimo 8 caracteres"
          minLength={8}
          maxLength={72}
          required
          disabled={loading}
        />
        <small>Tu cuenta se crea siempre con el rol cliente.</small>
      </div>

      <div className="field">
        <label htmlFor="register-confirm">Repite la contraseña</label>
        <input
          id="register-confirm"
          name="confirm"
          type="password"
          autoComplete="new-password"
          placeholder="Repite tu contraseña"
          minLength={8}
          maxLength={72}
          required
          disabled={loading}
        />
      </div>

      <button className="button" type="submit" disabled={loading}>
        {loading ? 'Creando cuenta…' : 'Crear cuenta'}
      </button>
    </form>
  );
}
