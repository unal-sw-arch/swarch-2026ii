import React from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { useImplement } from '../../api/hooks/useImplements';
import {
  FACULTY_LABELS,
  IMPLEMENT_CATEGORY_LABELS,
  IMPLEMENT_STATE_LABELS,
} from '../../types/equipment';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';
import { fadeUpBlur } from '../../shared/motion/motionTokens';
import { useToast } from '../../shared/context/ToastContext';

export function EquipmentDetailPage(): React.JSX.Element {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { profile, token } = useAuth();
  const { implement, loading, error, refetch } = useImplement(id || null, token);
  const toast = useToast();

  const isSanctioned = profile?.status === 'SAN';

  const handleCopyLink = async () => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(window.location.href);
        toast.success('¡Enlace copiado!', 'Puedes compartir este implemento con tu equipo.');
      } else {
        toast.info('Enlace al implemento', window.location.href);
      }
    } catch {
      toast.info('Enlace al implemento', window.location.href);
    }
  };

  if (loading) {
    return <LoadingSpinner label="Cargando ficha del implemento…" fullScreen />;
  }

  if (error || !implement) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center font-sans">
        <ErrorAlert
          title="Implemento no encontrado"
          message={error?.message || 'El implemento solicitado no existe o no está registrado en el sistema.'}
          onRetry={refetch}
        />
        <div className="mt-6">
          <button
            type="button"
            onClick={() => navigate('/equipment')}
            className="heroui-btn-solid inline-flex items-center gap-2 px-5 py-2.5 font-bold text-sm"
          >
            <span className="material-symbols-outlined text-lg" aria-hidden="true">
              arrow_back
            </span>
            Volver al catálogo
          </button>
        </div>
      </div>
    );
  }

  const isAvailable = implement.state === 'DIS';

  const stateColorMap = {
    DIS: 'bg-[#00D592]/20 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    RES: 'bg-amber-500/20 text-amber-800 border border-amber-500/30 dark:text-amber-300',
    PRE: 'bg-blue-500/20 text-blue-800 border border-blue-500/30 dark:text-blue-300',
    NDS: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300',
  };

  return (
    <motion.div
      variants={fadeUpBlur}
      initial="initial"
      animate="animate"
      className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8 font-sans"
    >
      {/* Botón Volver y Compartir */}
      <div className="mb-6 flex items-center justify-between">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 font-bold text-xs text-gray-500 hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            arrow_back
          </span>
          Volver
        </button>

        <button
          type="button"
          onClick={handleCopyLink}
          className="inline-flex items-center gap-1.5 font-bold text-xs text-neutral-600 hover:text-[#00BA7C] dark:text-neutral-400 dark:hover:text-[#00F5A0] transition-colors"
        >
          <span className="material-symbols-outlined text-base">share</span>
          Compartir
        </button>
      </div>

      <div className="heroui-card p-6 sm:p-8">
        <div className="grid grid-cols-1 gap-10 md:grid-cols-2">
          {/* Imagen del implemento */}
          <div className="relative aspect-4/3 w-full overflow-hidden rounded-3xl bg-gray-100 shadow-sm dark:bg-gray-800 border border-[#00D592]/20">
            {implement.image ? (
              <img
                src={implement.image}
                alt={`Fotografía de ${implement.name}`}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center bg-gradient-to-tr from-[#00D592]/10 via-[#00BA7C]/5 to-transparent text-[#00BA7C]">
                <span className="material-symbols-outlined text-8xl opacity-75" aria-hidden="true">
                  sports_soccer
                </span>
              </div>
            )}
          </div>

          {/* Ficha técnica y acción de reserva */}
          <div className="flex flex-col">
            {/* Chips: Estado y Facultad */}
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`rounded-full px-3 py-1 font-bold text-xs uppercase shadow-xs ${
                  stateColorMap[implement.state] || 'bg-gray-100 text-gray-800'
                }`}
              >
                {IMPLEMENT_STATE_LABELS[implement.state] || implement.state}
              </span>

              <span className="heroui-chip">
                {FACULTY_LABELS[implement.faculty] || implement.faculty}
              </span>
            </div>

            {/* Nombre H1 */}
            <h1 className="mt-4 font-display font-extrabold text-3xl tracking-tight text-gray-900 dark:text-white">
              {implement.name}
            </h1>

            {/* Categoría */}
            {implement.category && (
              <p className="mt-1 font-bold text-xs uppercase tracking-wider text-[#00875A] dark:text-[#00F5A0]">
                Categoría: {IMPLEMENT_CATEGORY_LABELS[implement.category] || implement.category}
              </p>
            )}

            {/* Descripción */}
            <div className="mt-4">
              <h2 className="font-bold text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Descripción del material
              </h2>
              <p className="mt-1 text-sm text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-line">
                {implement.description}
              </p>
            </div>

            {/* Alerta si el usuario está sancionado */}
            {isSanctioned && (
              <div
                role="alert"
                className="mt-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 text-xs text-red-800 dark:text-red-300"
              >
                <div className="flex items-center gap-2 font-bold">
                  <span className="material-symbols-outlined text-lg text-[#FF4D4F]">
                    error
                  </span>
                  No puedes reservar este implemento
                </div>
                <p className="mt-1">
                  Tu usuario registra una sanción activa en el sistema. Ponte en contacto con la administración de tu facultad para solucionar la sanción.
                </p>
              </div>
            )}

            {/* Botón de Reserva HeroUI */}
            <div className="mt-auto pt-6 border-t border-[#00D592]/15 dark:border-gray-800">
              {isAvailable ? (
                isSanctioned ? (
                  <button
                    type="button"
                    disabled
                    className="w-full rounded-full bg-gray-200 px-6 py-3 font-bold text-sm text-gray-500 opacity-60 cursor-not-allowed dark:bg-gray-800"
                  >
                    Reserva bloqueada por sanción
                  </button>
                ) : (
                  <Link
                    to={`/equipment/${implement.id}/reserve`}
                    className="heroui-btn-solid flex w-full items-center justify-center gap-2 px-6 py-3.5 text-sm font-bold shadow-lg shadow-[#00D592]/30"
                  >
                    <span className="material-symbols-outlined text-xl" aria-hidden="true">
                      timer
                    </span>
                    Reservar este implemento (10 min de recogida)
                  </Link>
                )
              ) : (
                <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 text-center text-sm text-gray-600 dark:border-gray-800 dark:bg-[#111A24] dark:text-gray-400">
                  <p>
                    Este implemento se encuentra actualmente en estado{' '}
                    <strong className="text-gray-900 dark:text-gray-100 uppercase">
                      {IMPLEMENT_STATE_LABELS[implement.state]}
                    </strong>
                    . Revisa más tarde cuando sea devuelto a la taquilla.
                  </p>
                  <div className="mt-3 pt-3 border-t border-gray-200/60 dark:border-gray-800">
                    <Link
                      to={`/equipment?category=${implement.category || ''}`}
                      className="inline-flex items-center gap-1 text-xs font-bold text-[#00875A] dark:text-[#00F5A0] hover:underline"
                    >
                      Ver otros implementos de esta categoría
                      <span className="material-symbols-outlined text-xs">arrow_forward</span>
                    </Link>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
