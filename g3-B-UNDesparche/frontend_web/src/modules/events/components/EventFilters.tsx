import React, { useRef, useEffect } from 'react';
import { EVENT_CATEGORY_LABELS, EVENT_STATUS_LABELS } from '../../../types/event';

export interface EventFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  category: string;
  onCategoryChange: (value: string) => void;
  status: string;
  onStatusChange: (value: string) => void;
  onResetFilters?: () => void;
}

export function EventFilters({
  search,
  onSearchChange,
  category,
  onCategoryChange,
  status,
  onStatusChange,
  onResetFilters,
}: EventFiltersProps): React.JSX.Element {
  const inputRef = useRef<HTMLInputElement>(null);
  const hasActiveFilters = Boolean(search || category || status);

  // Atajo de teclado: pulsar "/" enfoca el buscador si no se está escribiendo en otro input
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        e.key === '/' &&
        document.activeElement?.tagName !== 'INPUT' &&
        document.activeElement?.tagName !== 'TEXTAREA' &&
        document.activeElement?.tagName !== 'SELECT'
      ) {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleReset = () => {
    onSearchChange('');
    onCategoryChange('');
    onStatusChange('');
    if (onResetFilters) onResetFilters();
  };

  return (
    <div className="heroui-card flex flex-col gap-3 p-3.5 sm:p-4 rounded-3xl sm:flex-row sm:items-center">
      {/* Campo de búsqueda con atajo y botón de limpieza */}
      <div className="relative flex-1">
        <label htmlFor="events-search-input" className="sr-only">
          Buscar eventos
        </label>
        <span
          className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-[#00D592]"
          aria-hidden="true"
        >
          <span className="material-symbols-outlined text-xl">search</span>
        </span>
        <input
          ref={inputRef}
          id="events-search-input"
          type="search"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Buscar por nombre, lugar o descripción… (Presiona /)"
          className="w-full rounded-2xl border border-gray-200/90 bg-white/70 py-2.5 pr-9 pl-10.5 text-sm placeholder-gray-400 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 transition-all dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-white"
        />
        {search && (
          <button
            type="button"
            onClick={() => onSearchChange('')}
            aria-label="Limpiar búsqueda"
            className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        )}
      </div>

      {/* Selector de Categoría */}
      <div className="w-full sm:w-56">
        <label htmlFor="events-category-select" className="sr-only">
          Filtrar por categoría
        </label>
        <select
          id="events-category-select"
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          className="w-full rounded-2xl border border-gray-200/90 bg-white/70 px-4 py-2.5 text-sm text-gray-700 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 transition-all dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
        >
          <option value="">Todas las categorías…</option>
          {Object.entries(EVENT_CATEGORY_LABELS).map(([catKey, label]) => (
            <option key={catKey} value={catKey}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {/* Selector de Estado */}
      <div className="w-full sm:w-52">
        <label htmlFor="events-status-select" className="sr-only">
          Filtrar por estado
        </label>
        <select
          id="events-status-select"
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
          className="w-full rounded-2xl border border-gray-200/90 bg-white/70 px-4 py-2.5 text-sm text-gray-700 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 transition-all dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
        >
          <option value="">Todos los estados…</option>
          {Object.entries(EVENT_STATUS_LABELS).map(([stKey, label]) => (
            <option key={stKey} value={stKey}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {/* Botón de Restablecer si hay filtros activos */}
      {hasActiveFilters && (
        <button
          type="button"
          onClick={handleReset}
          className="inline-flex items-center justify-center gap-1 rounded-2xl px-3.5 py-2.5 text-xs font-semibold text-neutral-600 hover:text-neutral-900 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:text-white dark:hover:bg-neutral-800 transition-colors shrink-0"
        >
          <span className="material-symbols-outlined text-base">restart_alt</span>
          Limpiar
        </button>
      )}
    </div>
  );
}
