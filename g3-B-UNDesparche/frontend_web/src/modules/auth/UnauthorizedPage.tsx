import React from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../../context/AuthContext';

export function UnauthorizedPage(): React.JSX.Element {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center px-4 py-16 text-center">
      <div className="flex h-20 w-20 items-center justify-center rounded-full bg-red-100 text-[var(--color-undesparche-danger)] dark:bg-red-950/60">
        <span className="material-symbols-outlined text-4xl" aria-hidden="true">
          gpp_bad
        </span>
      </div>

      <h1 className="mt-6 font-display font-bold text-3xl tracking-tight text-gray-900 dark:text-gray-100">
        Acceso no autorizado
      </h1>

      <p className="mt-3 max-w-md text-base text-gray-600 dark:text-gray-400">
        No tienes los permisos requeridos para acceder a esta sección de la plataforma. Si crees que se trata de un error, comunícate con el Administrador del Sistema.
      </p>

      <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
        <Link
          to="/"
          className="inline-flex items-center gap-2 rounded-[var(--radius-undesparche)] bg-[var(--color-undesparche-primary)] px-5 py-2.5 font-medium text-sm text-white shadow-xs hover:bg-[var(--color-undesparche-primary-dark)] focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)]"
        >
          <span className="material-symbols-outlined text-lg" aria-hidden="true">
            home
          </span>
          Volver al Inicio
        </Link>

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex items-center gap-2 rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-5 py-2.5 font-medium text-sm text-gray-700 shadow-xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          <span className="material-symbols-outlined text-lg" aria-hidden="true">
            logout
          </span>
          Cerrar Sesión
        </button>
      </div>
    </div>
  );
}
