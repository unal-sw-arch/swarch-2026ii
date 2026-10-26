import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { useImplement, useCreateReserve } from '../../api/hooks/useImplements';
import { FACULTY_LABELS } from '../../types/equipment';
import { ReservationTimer } from './components/ReservationTimer';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';
import { useToast } from '../../shared/context/ToastContext';

export function ReservationPage(): React.JSX.Element {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { token, profile } = useAuth();
  const { implement, loading: loadingImplement, error: implementError } = useImplement(id || null, token);
  const { mutate, loading: reserving, error: reserveError, reserve } = useCreateReserve(token);
  const toast = useToast();

  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const isSanctioned = profile?.status === 'SAN';

  if (loadingImplement) {
    return <LoadingSpinner label="Preparando reserva del implemento…" fullScreen />;
  }

  if (implementError || !implement) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center">
        <ErrorAlert
          title="No fue posible preparar la reserva"
          message={implementError?.message || 'El implemento solicitado no se encuentra disponible.'}
        />
        <div className="mt-6">
          <Link
            to="/equipment"
            className="heroui-btn-solid inline-flex items-center gap-2 px-5 py-2.5 font-bold text-sm"
          >
            Volver al catálogo de implementos
          </Link>
        </div>
      </div>
    );
  }

  // Si el implemento no está en estado DISponible
  if (implement.state !== 'DIS') {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center">
        <div className="heroui-card p-8 border-amber-400/40 bg-amber-500/10 text-amber-900 dark:text-amber-200">
          <span className="material-symbols-outlined text-4xl text-[#F5A623]" aria-hidden="true">
            warning
          </span>
          <h2 className="mt-4 font-display font-bold text-xl">
            Este implemento ya no está disponible
          </h2>
          <p className="mt-2 text-sm text-amber-800/90 dark:text-amber-300">
            El implemento &quot;{implement.name}&quot; cambió de estado recientemente a{' '}
            <strong className="uppercase">{implement.state}</strong>.
          </p>
          <div className="mt-6 flex justify-center gap-4">
            <Link
              to={`/equipment/${implement.id}`}
              className="heroui-btn-flat px-4 py-2 font-bold text-xs"
            >
              Ver detalle del implemento
            </Link>
            <Link
              to="/equipment"
              className="heroui-btn-solid px-4 py-2 font-bold text-xs"
            >
              Buscar otros implementos
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // Si el usuario está sancionado
  if (isSanctioned) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center">
        <div className="heroui-card p-8 border-red-500/40 bg-red-500/10 text-red-900 dark:text-red-200">
          <span className="material-symbols-outlined text-4xl text-[#FF4D4F]" aria-hidden="true">
            gpp_bad
          </span>
          <h2 className="mt-4 font-display font-bold text-xl">
            Reserva bloqueada por sanción
          </h2>
          <p className="mt-2 text-sm text-red-800 dark:text-red-300">
            Tu cuenta posee una penalización activa en la plataforma de préstamos de la UNAL. Contacta a la coordinación de deportes de tu facultad para levantar la sanción.
          </p>
          <div className="mt-6">
            <Link
              to="/equipment"
              className="heroui-btn-flat px-4 py-2 font-bold text-xs"
            >
              Volver al catálogo
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const handleConfirmReservation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!acceptedTerms || reserving) return;
    try {
      await mutate(implement.id);
      toast.success(
        '¡Reserva confirmada con éxito!',
        'Tienes 10 minutos para reclamar tu implemento con tu carné institucional.'
      );
    } catch {
      // El error se maneja reactivamente en reserveError
    }
  };

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8 font-sans">
      {/* Navegación de retorno */}
      <div className="mb-6">
        <Link
          to={`/equipment/${implement.id}`}
          className="inline-flex items-center gap-1.5 font-bold text-xs text-gray-500 hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            arrow_back
          </span>
          Cancelar y volver a la ficha
        </Link>
      </div>

      {reserve ? (
        /* VISTA DE ÉXITO: RESERVA ACTIVA CON TEMPORIZADOR */
        <div className="space-y-6">
          <div className="heroui-card p-6 sm:p-8">
            <div className="flex items-center gap-3.5">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#00D592]/20 text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
                <span className="material-symbols-outlined text-3xl" aria-hidden="true">
                  check_circle
                </span>
              </div>
              <div>
                <h1 className="font-display font-extrabold text-2xl text-gray-900 dark:text-white">
                  ¡Reserva confirmada con éxito!
                </h1>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  Implemento: <strong className="text-gray-900 dark:text-gray-100">{implement.name}</strong> · {FACULTY_LABELS[implement.faculty]}
                </p>
              </div>
            </div>

            {/* Temporizador de 10 minutos en vivo */}
            <div className="mt-6">
              <ReservationTimer
                expirationTime={reserve.datetime_expiration || new Date(Date.now() + 10 * 60 * 1000).toISOString()}
                onExpire={() => {
                  console.info('La reserva de 10 minutos ha expirado.');
                }}
              />
            </div>

            {/* Instrucciones de recogida presencial */}
            <div className="mt-6 rounded-2xl bg-[#00D592]/5 p-4.5 border border-[#00D592]/20">
              <h2 className="font-bold text-xs uppercase tracking-wider text-[#00875A] dark:text-[#00F5A0]">
                Pasos para reclamar tu implemento:
              </h2>
              <ol className="mt-2.5 space-y-2 text-xs text-gray-600 dark:text-gray-300 list-decimal list-inside leading-relaxed">
                <li>
                  Dirígete de inmediato al punto de entrega de <strong>{FACULTY_LABELS[implement.faculty]}</strong>.
                </li>
                <li>
                  Presenta tu carné de la Universidad Nacional de Colombia en físico o digital.
                </li>
                <li>
                  El encargado verificará tu reserva y te entregará el material.
                </li>
              </ol>
            </div>

            <div className="mt-6 flex flex-wrap justify-between items-center gap-4 border-t border-[#00D592]/15 pt-5 dark:border-gray-800">
              <button
                type="button"
                onClick={() => navigate('/equipment')}
                className="heroui-btn-solid px-5 py-2 text-xs font-bold"
              >
                Volver al catálogo
              </button>
              <Link
                to={`/equipment/${implement.id}`}
                className="font-bold text-xs text-gray-600 hover:text-[#00BA7C] dark:text-gray-400 dark:hover:text-[#00F5A0] transition-colors"
              >
                Ver ficha del implemento
              </Link>
            </div>
          </div>
        </div>
      ) : (
        /* FORMULARIO DE RESERVA */
        <div className="heroui-card p-6 sm:p-8">
          <div className="flex items-center gap-4 border-b border-[#00D592]/15 pb-5 dark:border-gray-800">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
              <span className="material-symbols-outlined text-3xl" aria-hidden="true">
                assignment
              </span>
            </div>
            <div>
              <span className="text-xs font-bold text-[#00875A] dark:text-[#00F5A0] uppercase tracking-wider">
                Reserva exprés de implemento
              </span>
              <h1 className="font-display font-extrabold text-2xl text-gray-900 dark:text-white">
                {implement.name}
              </h1>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Punto de entrega: <strong>{FACULTY_LABELS[implement.faculty]}</strong>
              </p>
            </div>
          </div>

          {reserveError && (
            <div className="mt-5">
              <ErrorAlert
                title="No se pudo procesar la reserva"
                message={reserveError.message}
              />
            </div>
          )}

          {/* Términos y condiciones del préstamo */}
          <div className="mt-6 rounded-2xl border border-[#00D592]/20 bg-[#00D592]/5 p-5">
            <h2 className="font-bold text-xs tracking-wider uppercase text-[#00875A] dark:text-[#00F5A0]">
              Reglamento y Condiciones de Préstamo UNAL
            </h2>
            <ul className="mt-3 space-y-2.5 text-xs text-gray-600 dark:text-gray-300">
              <li className="flex items-start gap-2">
                <span className="material-symbols-outlined text-base text-[#00D592] shrink-0" aria-hidden="true">
                  schedule
                </span>
                <span>
                  <strong>Ventana de 10 minutos:</strong> Tienes exactamente 10 minutos para presentarte en el punto de entrega. Si no te presentas en ese lapso, la reserva expirará automáticamente.
                </span>
              </li>
              <li className="flex items-start gap-2">
                <span className="material-symbols-outlined text-base text-[#00D592] shrink-0" aria-hidden="true">
                  badge
                </span>
                <span>
                  <strong>Carné institucional obligatorio:</strong> Debes presentar tu carné vigente de la Universidad Nacional de Colombia al momento de la entrega.
                </span>
              </li>
              <li className="flex items-start gap-2">
                <span className="material-symbols-outlined text-base text-[#00D592] shrink-0" aria-hidden="true">
                  warning
                </span>
                <span>
                  <strong>Responsabilidad y Sanciones:</strong> El implemento debe devolverse en las mismas condiciones. La no devolución acarreará estado sancionado (<code className="font-bold text-red-500">SAN</code>) en tu perfil.
                </span>
              </li>
            </ul>
          </div>

          <form onSubmit={handleConfirmReservation} className="mt-8 space-y-6">
            <div className="flex items-start gap-3">
              <input
                id="terms-checkbox"
                type="checkbox"
                required
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
                className="mt-1 h-4 w-4 rounded accent-[#00D592] cursor-pointer"
              />
              <label
                htmlFor="terms-checkbox"
                className="font-medium text-xs text-gray-700 dark:text-gray-300 leading-snug cursor-pointer"
              >
                He leído y acepto el reglamento de préstamos. Me comprometo a presentarme en los próximos 10 minutos en la facultad correspondiente con mi carné estudiantil.
              </label>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#00D592]/15 dark:border-gray-800">
              <button
                type="button"
                onClick={() => navigate(`/equipment/${implement.id}`)}
                className="heroui-btn-flat px-4 py-2 text-xs font-bold"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={!acceptedTerms || reserving}
                className="heroui-btn-solid inline-flex items-center gap-2 px-6 py-2.5 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-50"
              >
                {reserving ? (
                  <span className="material-symbols-outlined animate-spin text-base" aria-hidden="true">
                    progress_activity
                  </span>
                ) : (
                  <span className="material-symbols-outlined text-base" aria-hidden="true">
                    check
                  </span>
                )}
                <span>{reserving ? 'Confirmando reserva…' : 'Confirmar reserva (10 min)'}</span>
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
