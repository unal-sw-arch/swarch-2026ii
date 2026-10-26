import React from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { useEvents } from '../../api/hooks/useEvents';
import { EventFilters } from './components/EventFilters';
import { EventCard } from './components/EventCard';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { fadeUpBlur, staggerContainer } from '../../shared/motion/motionTokens';

export function EventsListPage(): React.JSX.Element {
  const { token } = useAuth();
  const {
    events,
    loading,
    error,
    search,
    setSearch,
    categoryFilter,
    setCategoryFilter,
    statusFilter,
    setStatusFilter,
    refresh,
  } = useEvents(token);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 font-sans">
      {/* Encabezado */}
      <motion.div
        variants={fadeUpBlur}
        initial="initial"
        animate="animate"
        className="mb-8"
      >
        <h1 className="font-display font-bold text-3xl tracking-tight text-gray-900 sm:text-4xl dark:text-white">
          Eventos del Campus
        </h1>
        <p className="mt-2 text-base text-gray-600 dark:text-gray-400 max-w-3xl">
          Descubre actividades académicas, culturales, asambleas y encuentros recreativos en la Universidad Nacional de Colombia.
        </p>
      </motion.div>

      {/* Barra de filtros */}
      <div className="mb-6">
        <EventFilters
          search={search}
          onSearchChange={setSearch}
          category={categoryFilter}
          onCategoryChange={setCategoryFilter}
          status={statusFilter}
          onStatusChange={setStatusFilter}
          onResetFilters={() => {
            setSearch('');
            setCategoryFilter('');
            setStatusFilter('');
          }}
        />
      </div>

      {!loading && !error && events.length > 0 && (
        <div className="mb-5 flex items-center justify-between text-xs text-neutral-500 dark:text-neutral-400">
          <span className="font-semibold">
            Mostrando {events.length} {events.length === 1 ? 'actividad activa' : 'actividades activas'}
          </span>
          {(search || categoryFilter || statusFilter) && (
            <button
              type="button"
              onClick={() => {
                setSearch('');
                setCategoryFilter('');
                setStatusFilter('');
              }}
              className="text-[#00875A] dark:text-[#00F5A0] hover:underline font-semibold"
            >
              Restablecer todos los filtros
            </button>
          )}
        </div>
      )}

      {/* Estados de carga / error / contenido */}
      {loading ? (
        <div className="py-16">
          <LoadingSpinner label="Buscando eventos en el campus…" />
        </div>
      ) : error ? (
        <ErrorAlert
          title="No pudimos cargar los eventos"
          message={error.message}
          onRetry={refresh}
        />
      ) : events.length === 0 ? (
        <EmptyState
          icon="event_busy"
          title="No se encontraron eventos"
          description="Intenta cambiar los términos de búsqueda o ajustar los filtros de categoría y estado."
          actionLabel="Limpiar filtros"
          onAction={() => {
            setSearch('');
            setCategoryFilter('');
            setStatusFilter('');
          }}
        />
      ) : (
        <motion.div
          variants={staggerContainer}
          initial="initial"
          animate="animate"
          className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
        >
          {events.map((event) => (
            <EventCard key={event.id} event={event} />
          ))}
        </motion.div>
      )}
    </div>
  );
}
