import React, { useEffect, useState, useRef } from 'react';
import { useSearchParams, Link } from 'react-router';
import { unsubscribeWithToken, EmailUnsubscribeResponse } from '../../api/events';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';

export function UnsubscribePage(): React.JSX.Element {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [loading, setLoading] = useState<boolean>(true);
  const [result, setResult] = useState<EmailUnsubscribeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Guarda con useRef para asegurar que se ejecute estrictamente una sola vez en React 19
  const calledRef = useRef(false);

  useEffect(() => {
    if (calledRef.current) return;
    calledRef.current = true;

    if (!token) {
      setLoading(false);
      setError('Enlace inválido o incompleto. No se proporcionó el token de desuscripción.');
      return;
    }

    const processUnsubscribe = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await unsubscribeWithToken(token);
        setResult(res);
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'No fue posible procesar la desuscripción. El enlace puede haber vencido o ser inválido.'
        );
      } finally {
        setLoading(false);
      }
    };

    processUnsubscribe();
  }, [token]);

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-4 py-16 text-center sm:px-6 lg:px-8">
      <div className="w-full max-w-md rounded-[var(--radius-undesparche)] bg-white p-8 shadow-sm ring-1 ring-gray-200 dark:bg-gray-900 dark:ring-gray-800">
        {loading ? (
          <LoadingSpinner label="Procesando desuscripción…" />
        ) : error ? (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-100 text-[var(--color-undesparche-danger)] dark:bg-red-950/60">
              <span className="material-symbols-outlined text-3xl" aria-hidden="true">
                link_off
              </span>
            </div>
            <h1 className="mt-4 font-display font-bold text-xl text-gray-900 dark:text-gray-100">
              Enlace inválido
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">{error}</p>
          </div>
        ) : result?.already ? (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 text-[var(--color-undesparche-warning)] dark:bg-amber-950/60">
              <span className="material-symbols-outlined text-3xl" aria-hidden="true">
                notifications_off
              </span>
            </div>
            <h1 className="mt-4 font-display font-bold text-xl text-gray-900 dark:text-gray-100">
              Ya no estabas suscrito
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              {result.event_name
                ? `Ya habías cancelado previamente las notificaciones de "${result.event_name}".`
                : 'Tu correo ya no se encuentra en la lista de notificaciones.'}
            </p>
          </div>
        ) : (
          <div>
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-[var(--color-undesparche-success)] dark:bg-emerald-950/60">
              <span className="material-symbols-outlined text-3xl" aria-hidden="true">
                check_circle
              </span>
            </div>
            <h1 className="mt-4 font-display font-bold text-xl text-gray-900 dark:text-gray-100">
              Listo, te desuscribiste
            </h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              {result?.event_name
                ? `No recibirás más correos sobre el evento "${result.event_name}".`
                : 'Te has desuscrito correctamente.'}
            </p>
          </div>
        )}

        <div className="mt-8 border-t border-gray-100 pt-5 dark:border-gray-800">
          <Link
            to="/events"
            className="inline-flex items-center gap-2 rounded-[var(--radius-undesparche)] bg-[var(--color-undesparche-primary)] px-4 py-2 font-medium text-sm text-white shadow-xs hover:bg-[var(--color-undesparche-primary-dark)] focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)]"
          >
            <span className="material-symbols-outlined text-lg" aria-hidden="true">
              calendar_month
            </span>
            Explorar más eventos del campus
          </Link>
        </div>
      </div>
    </div>
  );
}
