import React, { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';

export function LoginPage(): React.JSX.Element {
  const { isAuthenticated, isLoading, signInWithGoogle, error: authError } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

  if (isLoading) {
    return <LoadingSpinner label="Verificando sesión…" fullScreen />;
  }

  if (isAuthenticated) {
    return <Navigate to={from} replace />;
  }

  const handleGoogleSignIn = async () => {
    setSubmitting(true);
    setLocalError(null);
    try {
      await signInWithGoogle();
      navigate(from, { replace: true });
    } catch (err: any) {
      if (err?.code !== 'auth/popup-closed-by-user') {
        setLocalError(
          err instanceof Error
            ? err.message
            : 'Ocurrió un error al iniciar sesión con Google.'
        );
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-gradient-to-b from-[#F0FDF8] via-white to-white px-4 py-12 dark:from-[#080E14] dark:via-[#0A121A] dark:to-[#080E14] sm:px-6 lg:px-8 font-sans">
      {/* Glow de fondo HeroUI */}
      <div
        className="pointer-events-none absolute -top-40 left-1/2 h-[500px] w-[700px] -translate-x-1/2 rounded-full bg-gradient-to-b from-[#00D592]/25 to-transparent blur-3xl"
        aria-hidden="true"
      />

      <div className="heroui-card relative w-full max-w-lg space-y-7 p-8 sm:p-10 shadow-2xl backdrop-blur-2xl">
        {/* Header con sello UNAL y título */}
        <div className="flex flex-col items-center text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-tr from-[#00D592] to-[#00F5A0] text-[#032B1D] shadow-lg shadow-[#00D592]/30">
            <span className="material-symbols-outlined text-3xl font-bold" aria-hidden="true">
              local_activity
            </span>
          </div>

          <h1 className="mt-5 font-display text-2xl sm:text-3xl font-extrabold tracking-tight text-gray-900 dark:text-white">
            UNDesparche
          </h1>
          <div className="mt-1.5 inline-flex items-center gap-1.5 rounded-full bg-[#00D592]/15 px-3 py-0.5 text-xs font-bold text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
            Universidad Nacional de Colombia · Sede Bogotá
          </div>
          <p className="mt-3 text-xs sm:text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
            Inicia sesión con tu correo institucional <strong className="text-gray-900 dark:text-gray-200 font-bold">@unal.edu.co</strong> para acceder a los eventos y reserva de implementos deportivos.
          </p>
        </div>

        {/* Alerta de error */}
        {(localError || authError) && (
          <ErrorAlert
            title="Error de inicio de sesión"
            message={localError || authError || ''}
            className="mt-4"
          />
        )}

        {/* Botón oficial de Google HeroUI Style */}
        <div className="mt-6">
          <button
            type="button"
            disabled={submitting}
            onClick={handleGoogleSignIn}
            className="flex w-full items-center justify-center gap-3 rounded-full border border-gray-200 bg-white px-5 py-3.5 font-bold text-sm text-gray-800 shadow-sm hover:bg-gray-50 hover:border-[#00D592] focus-visible:outline-2 focus-visible:outline-[#00D592] disabled:cursor-not-allowed disabled:opacity-60 dark:border-gray-800 dark:bg-[#111A24] dark:text-gray-100 transition-all cursor-pointer"
          >
            {submitting ? (
              <span className="material-symbols-outlined animate-spin text-xl text-[#00D592]" aria-hidden="true">
                progress_activity
              </span>
            ) : (
              <svg className="h-5 w-5" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                />
              </svg>
            )}
            <span>
              {submitting ? 'Iniciando sesión…' : 'Continuar con Google (@unal.edu.co)'}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
