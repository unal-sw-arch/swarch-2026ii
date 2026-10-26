import React from 'react';

export interface PaginationProps {
  count: number;
  page: number; // 0-indexed
  rowsPerPage: number;
  rowsPerPageOptions?: number[];
  onPageChange: (newPage: number) => void;
  onRowsPerPageChange?: (newRowsPerPage: number) => void;
  className?: string;
}

export function Pagination({
  count,
  page,
  rowsPerPage,
  rowsPerPageOptions = [5, 10, 25, 50],
  onPageChange,
  onRowsPerPageChange,
  className = '',
}: PaginationProps): React.JSX.Element {
  const totalPages = Math.ceil(count / rowsPerPage) || 1;
  const startItem = count === 0 ? 0 : page * rowsPerPage + 1;
  const endItem = Math.min((page + 1) * rowsPerPage, count);

  return (
    <nav
      aria-label="Paginación de resultados"
      className={`flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-gray-200 px-3 py-3 sm:px-4 text-sm text-gray-700 dark:border-gray-800 dark:text-gray-300 ${className}`}
    >
      {/* Filas por página y rango de items */}
      <div className="flex items-center justify-between w-full sm:w-auto gap-3 sm:gap-4">
        <div className="flex items-center gap-2">
          <label htmlFor="rows-per-page-select" className="text-xs sm:text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap">
            Filas:
          </label>
          {onRowsPerPageChange && (
            <select
              id="rows-per-page-select"
              value={rowsPerPage}
              onChange={(e) => onRowsPerPageChange(Number(e.target.value))}
              className="rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-2 py-1 text-xs sm:text-sm shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800"
            >
              {rowsPerPageOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Rango X–Y de Z */}
        <div className="text-xs sm:text-sm tabular-nums text-gray-600 dark:text-gray-400 whitespace-nowrap">
          <span className="font-medium text-gray-900 dark:text-gray-100">{startItem}</span>
          {'–'}
          <span className="font-medium text-gray-900 dark:text-gray-100">{endItem}</span>
          {' de '}
          <span className="font-medium text-gray-900 dark:text-gray-100">{count}</span>
        </div>
      </div>

      {/* Botones de navegación con aria-label */}
      <div className="flex items-center justify-center w-full sm:w-auto gap-1">
        <button
          type="button"
          aria-label="Ir a la primera página"
          disabled={page === 0}
          onClick={() => onPageChange(0)}
          className="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white text-gray-700 shadow-2xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            first_page
          </span>
        </button>

        <button
          type="button"
          aria-label="Ir a la página anterior"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          className="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white text-gray-700 shadow-2xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            chevron_left
          </span>
        </button>

        <span className="px-2 text-xs sm:text-sm tabular-nums text-gray-700 dark:text-gray-300">
          Página {page + 1} de {totalPages}
        </span>

        <button
          type="button"
          aria-label="Ir a la página siguiente"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          className="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white text-gray-700 shadow-2xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            chevron_right
          </span>
        </button>

        <button
          type="button"
          aria-label="Ir a la última página"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(totalPages - 1)}
          className="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white text-gray-700 shadow-2xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            last_page
          </span>
        </button>
      </div>
    </nav>
  );
}
