import React from 'react';
import { useParams, Link } from 'react-router';
import { motion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext';
import { useEventDetail } from '../../api/hooks/useEvents';
import { EVENT_CATEGORY_LABELS, EVENT_STATUS_LABELS } from '../../types/event';
import { EventMap } from './components/EventMap';
import { SubscriptionButton } from './components/SubscriptionButton';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../shared/components/ui/ErrorAlert';
import { fadeUpBlur } from '../../shared/motion/motionTokens';
import { useToast } from '../../shared/context/ToastContext';

export function EventDetailPage(): React.JSX.Element {
  const { id } = useParams<{ id: string }>();
  const { token } = useAuth();
  const { event, loading, error, refetch } = useEventDetail(id, token);
  const toast = useToast();

  const handleCopyLink = async () => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(window.location.href);
        toast.success('¡Enlace copiado!', 'El link al evento está listo para compartir.');
      } else {
        toast.info('URL del evento', window.location.href);
      }
    } catch {
      toast.info('URL del evento', window.location.href);
    }
  };

  if (loading) {
    return <LoadingSpinner label="Cargando detalles del evento…" fullScreen />;
  }

  if (error || !event) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center font-sans">
        <ErrorAlert
          title="No pudimos encontrar el evento"
          message={error?.message || 'El evento no existe o fue retirado.'}
          onRetry={refetch}
        />
        <div className="mt-6">
          <Link
            to="/events"
            className="heroui-btn-solid inline-flex items-center gap-2 px-5 py-2.5 font-bold text-sm"
          >
            <span className="material-symbols-outlined text-lg" aria-hidden="true">
              arrow_back
            </span>
            Volver a Eventos
          </Link>
        </div>
      </div>
    );
  }

  const startDateFormatted = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(event.datetime_start));

  const endDateFormatted = new Intl.DateTimeFormat('es-CO', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(event.datetime_end));

  const mapsUrl = `https://www.google.com/maps?q=${event.latitude},${event.longitude}`;

  const statusColorMap = {
    PRO: 'bg-[#00D592]/20 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    ECU: 'bg-emerald-500/20 text-emerald-800 border border-emerald-500/30 dark:text-emerald-300',
    CAN: 'bg-red-500/20 text-red-700 border border-red-500/30 dark:text-red-300',
    FIN: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300',
  };

  return (
    <motion.div
      variants={fadeUpBlur}
      initial="initial"
      animate="animate"
      className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 font-sans"
    >
      {/* Breadcrumb de regreso */}
      <nav aria-label="Navegación secundaria" className="mb-6">
        <Link
          to="/events"
          className="inline-flex items-center gap-1.5 font-bold text-xs text-gray-500 hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            arrow_back
          </span>
          Volver a todos los eventos
        </Link>
      </nav>

      <div className="grid grid-cols-1 gap-10 lg:grid-cols-3">
        {/* Columna Principal: Detalle, Imagen, Organizador */}
        <div className="lg:col-span-2 space-y-6">
          {/* Hero de Imagen */}
          <div className="relative aspect-16/9 w-full overflow-hidden rounded-3xl bg-gray-100 shadow-md dark:bg-gray-800 border border-[#00D592]/15">
            {event.image ? (
              <img
                src={event.image}
                alt={`Imagen de ${event.name}`}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center bg-gradient-to-tr from-[#00D592]/10 via-[#00BA7C]/5 to-transparent text-[#00BA7C]">
                <span className="material-symbols-outlined text-7xl opacity-70" aria-hidden="true">
                  event
                </span>
              </div>
            )}

            {/* Chips superpuestos */}
            <div className="absolute top-4 left-4 flex flex-wrap gap-2">
              {event.category && (
                <span className="rounded-full bg-black/60 px-3 py-1 font-semibold text-xs text-white backdrop-blur-md border border-white/10">
                  {EVENT_CATEGORY_LABELS[event.category] || event.category}
                </span>
              )}
              <span
                className={`rounded-full px-3 py-1 font-bold text-xs shadow-xs backdrop-blur-md ${
                  statusColorMap[event.status] || 'bg-gray-100 text-gray-800'
                }`}
              >
                {EVENT_STATUS_LABELS[event.status] || event.status}
              </span>
            </div>
          </div>

          <div className="heroui-card p-6 sm:p-8">
            {/* Título */}
            <h1 className="font-display font-extrabold text-2xl text-gray-900 sm:text-3xl dark:text-white">
              {event.name}
            </h1>

            {/* Organizador */}
            {event.organizer && (
              <div className="mt-4 flex items-center gap-3 border-y border-[#00D592]/15 py-3 dark:border-gray-800">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-tr from-[#00D592] to-[#00BA7C] text-sm font-bold text-[#032B1D]">
                  {event.organizer.name.charAt(0).toUpperCase()}
                </div>
                <div>
                  <p className="font-bold text-xs text-gray-900 dark:text-gray-100">
                    Organizado por {event.organizer.name}
                  </p>
                  <p className="text-[11px] text-gray-500">{event.organizer.email}</p>
                </div>
              </div>
            )}

            {/* Descripción completa */}
            <div className="mt-6">
              <h2 className="font-display font-bold text-lg text-gray-900 dark:text-gray-100">
                Acerca de este evento
              </h2>
              <div className="mt-3 prose max-w-none text-base text-gray-700 whitespace-pre-line dark:text-gray-300 leading-relaxed">
                {event.description}
              </div>
            </div>
          </div>
        </div>

        {/* Columna Lateral Sticky: Fecha, Lugar, Mapa, Suscripción */}
        <div className="lg:col-span-1">
          <div className="heroui-card sticky top-24 p-6 sm:p-7 space-y-5">
            <h2 className="font-display font-extrabold text-lg text-gray-900 dark:text-gray-100">
              Detalles de la cita
            </h2>

            {/* Fecha y Hora */}
            <div className="flex items-start gap-3">
              <span className="material-symbols-outlined mt-0.5 text-xl text-[#00D592]" aria-hidden="true">
                calendar_today
              </span>
              <div>
                <p className="font-bold text-xs text-gray-500 uppercase tracking-wider">
                  Fecha y horario
                </p>
                <p className="mt-0.5 font-bold text-sm text-gray-900 capitalize dark:text-gray-100">
                  {startDateFormatted}
                </p>
                <p className="text-xs text-gray-500 tabular-nums">
                  Hasta las {endDateFormatted}
                </p>
              </div>
            </div>

            {/* Lugar */}
            <div className="flex items-start gap-3">
              <span className="material-symbols-outlined mt-0.5 text-xl text-[#00D592]" aria-hidden="true">
                pin_drop
              </span>
              <div>
                <p className="font-bold text-xs text-gray-500 uppercase tracking-wider">
                  Lugar
                </p>
                <p className="mt-0.5 font-bold text-sm text-gray-900 dark:text-gray-100">
                  {event.place}
                </p>
              </div>
            </div>

            {/* Mapa integrado */}
            <div className="overflow-hidden rounded-2xl border border-[#00D592]/20">
              <EventMap
                lat={event.latitude}
                lng={event.longitude}
                height="190px"
                placeName={event.place}
              />
            </div>

            {/* Enlace Cómo llegar y Compartir */}
            <div className="grid grid-cols-2 gap-2 text-center">
              <a
                href={mapsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="heroui-btn-flat inline-flex items-center gap-1.5 px-3 py-2 text-xs font-bold justify-center"
              >
                <span className="material-symbols-outlined text-sm" aria-hidden="true">
                  directions
                </span>
                ¿Cómo llegar?
              </a>
              <button
                type="button"
                onClick={handleCopyLink}
                className="heroui-btn-flat inline-flex items-center gap-1.5 px-3 py-2 text-xs font-bold justify-center"
              >
                <span className="material-symbols-outlined text-sm" aria-hidden="true">
                  share
                </span>
                Compartir
              </button>
            </div>

            {/* Botón de Suscripción grande */}
            <div className="border-t border-[#00D592]/15 pt-5 dark:border-gray-800">
              <SubscriptionButton
                eventId={event.id}
                eventName={event.name}
                eventStatus={event.status}
                published={event.published}
                organizerId={event.organizer?.id}
                size="lg"
              />
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}
