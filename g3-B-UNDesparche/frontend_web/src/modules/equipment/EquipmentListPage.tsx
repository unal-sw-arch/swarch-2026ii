import React from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { useImplements } from '../../api/hooks/useImplements';
import { EquipmentFilters } from './components/EquipmentFilters';
import { EquipmentCard } from './components/EquipmentCard';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { fadeUpBlur, staggerContainer } from '../../shared/motion/motionTokens';

export function EquipmentListPage(): React.JSX.Element {
  const { profile, token } = useAuth();
  const isSanctioned = profile?.status === 'SAN';

  const {
    implementsList,
    loading,
    error,
    search,
    setSearch,
    category,
    setCategory,
    faculty,
    setFaculty,
    refresh,
  } = useImplements(undefined, token);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 font-sans">
      {/* Alerta si el usuario se encuentra sancionado (SAN) */}
      {isSanctioned && (
        <div
          role="alert"
          className="heroui-card mb-8 p-5 border-red-500/30 bg-red-500/10 text-red-900 dark:text-red-200"
        >
          <div className="flex items-start gap-3">
            <span className="material-symbols-outlined text-2xl text-[#FF4D4F]" aria-hidden="true">
              gpp_bad
            </span>
            <div>
              <h2 className="font-display font-bold text-sm">
                Cuenta con sanción activa
              </h2>
              <p className="mt-1 text-xs text-red-800/90 dark:text-red-300">
                Tu perfil registra un estado sancionado (<code className="font-bold text-red-500">SAN</code>). No podrás reservar nuevos implementos deportivos o recreativos hasta regularizar tu situación con la administración de bienestar de tu facultad.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Header */}
      <motion.div
        variants={fadeUpBlur}
        initial="initial"
        animate="animate"
        className="mb-8"
      >
        <h1 className="font-display font-bold text-3xl tracking-tight text-gray-900 sm:text-4xl dark:text-white">
          Catálogo de Implementos
        </h1>
        <p className="mt-2 text-base text-gray-600 dark:text-gray-400 max-w-3xl">
          Préstamo descentralizado de material deportivo y recreativo por facultades en la Sede Bogotá. Reserva en línea y recoge en 10 minutos con tu carné.
        </p>
      </motion.div>

      {/* Filtros */}
      <div className="mb-6">
        <EquipmentFilters
          search={search}
          onSearchChange={setSearch}
          category={category}
          onCategoryChange={setCategory}
          faculty={faculty}
          onFacultyChange={setFaculty}
          onResetFilters={() => {
            setSearch('');
            setCategory('');
            setFaculty('');
          }}
        />
      </div>

      {!loading && !error && implementsList.length > 0 && (
        <div className="mb-5 flex items-center justify-between text-xs text-neutral-500 dark:text-neutral-400">
          <span className="font-semibold">
            Mostrando {implementsList.length} {implementsList.length === 1 ? 'implemento disponible' : 'implementos disponibles'}
          </span>
          {(search || category || faculty) && (
            <button
              type="button"
              onClick={() => {
                setSearch('');
                setCategory('');
                setFaculty('');
              }}
              className="text-[#00875A] dark:text-[#00F5A0] hover:underline font-semibold"
            >
              Restablecer todos los filtros
            </button>
          )}
        </div>
      )}

      {/* Contenido */}
      {loading ? (
        <div className="py-16">
          <LoadingSpinner label="Consultando inventario de implementos…" />
        </div>
      ) : error ? (
        <ErrorAlert
          title="Error al consultar el catálogo"
          message={error.message}
          onRetry={refresh}
        />
      ) : implementsList.length === 0 ? (
        <EmptyState
          icon="sports_tennis"
          title="No encontramos implementos disponibles"
          description="Prueba ajustando los filtros de facultad o buscando con otro término."
          actionLabel="Ver todos los implementos"
          onAction={() => {
            setSearch('');
            setCategory('');
            setFaculty('');
          }}
        />
      ) : (
        <motion.div
          variants={staggerContainer}
          initial="initial"
          animate="animate"
          className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
        >
          {implementsList.map((item) => (
            <EquipmentCard
              key={item.id}
              implement={item}
              reserveDisabled={isSanctioned}
            />
          ))}
        </motion.div>
      )}
    </div>
  );
}
