'use client';

import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import type { UserSummary } from '@/lib/types';

const STAFF_ROLES = new Set(['admin', 'supervisor', 'mesero', 'cocinero', 'despachador']);

type LoginFormProps = {
  next?: string;
};

type ErrorBody = {
  code?: string;
  message?: string;
};

function safeNext(value?: string): string | undefined {
  if (!value) return undefined;
  if (!value.startsWith('/') || value.startsWith('//')) return undefined;
  return value;
}

function homeFor(user: UserSummary): string {
  return user.roles.some((role) => STAFF_ROLES.has(role)) ? '/staff' : '/';
}

export default function LoginForm({ next }: LoginFormProps) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    const data = new FormData(event.currentTarget);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          email: String(data.get('email') ?? '').trim(),
          password: String(data.get('password') ?? ''),
        }),
      });

      const body = (await response.json().catch(() => null)) as
        | (ErrorBody & { user?: UserSummary })
        | null;

      if (!response.ok || !body?.user) {
        setError(body?.message ?? 'No se pudo iniciar sesión. Revisa tus datos.');
        return;
      }

      router.push(safeNext(next) ?? homeFor(body.user));
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
        <label htmlFor="login-email">Correo electrónico</label>
        <input
          id="login-email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="cliente@demo.com"
          required
          disabled={loading}
        />
      </div>

      <div className="field">
        <label htmlFor="login-password">Contraseña</label>
        <input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          placeholder="Tu contraseña"
          minLength={8}
          required
          disabled={loading}
        />
      </div>

      <button className="button" type="submit" disabled={loading}>
        {loading ? 'Ingresando…' : 'Ingresar'}
      </button>
    </form>
  );
}
