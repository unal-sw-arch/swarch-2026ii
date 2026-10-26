import React from 'react';

export interface ErrorAlertProps {
  title?: string;
  message: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorAlert({
  title = 'Ha ocurrido un error',
  message,
  onRetry,
  className = '',
}: ErrorAlertProps): React.JSX.Element {
  return (
    <div
      role="alert"
      aria-live="assertive"
      className={`rounded-[var(--radius-undesparche)] border border-red-200 bg-red-50 p-4 text-red-900 shadow-xs dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-200 ${className}`}
    >
      <div className="flex items-start gap-3">
        <span
          className="material-symbols-outlined text-xl text-[var(--color-undesparche-danger)]"
          aria-hidden="true"
        >
          error
        </span>
        <div className="flex-1">
          <h3 className="font-semibold text-sm leading-tight text-red-900 dark:text-red-200">
            {title}
          </h3>
          <p className="mt-1 text-sm text-red-800 dark:text-red-300">
            {message}
          </p>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="mt-3 inline-flex items-center gap-1.5 rounded-md bg-white px-2.5 py-1.5 font-medium text-xs text-red-700 shadow-xs ring-1 ring-inset ring-red-300 hover:bg-red-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:bg-red-900/40 dark:text-red-100 dark:ring-red-800"
            >
              <span className="material-symbols-outlined text-sm" aria-hidden="true">
                refresh
              </span>
              Reintentar
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
