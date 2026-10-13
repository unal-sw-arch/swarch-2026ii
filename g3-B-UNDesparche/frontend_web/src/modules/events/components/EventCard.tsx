import React from 'react';
import { Link } from 'react-router';
import { motion } from 'framer-motion';
import { CampusEvent, EVENT_CATEGORY_LABELS, EVENT_STATUS_LABELS } from '../../../types/event';
import { SubscriptionButton } from './SubscriptionButton';
import { staggerItem } from '../../../shared/motion/motionTokens';

export interface EventCardProps {
  event: CampusEvent;
}

export function EventCard({ event }: EventCardProps): React.JSX.Element {
  const formattedDate = new Intl.DateTimeFormat('es-CO', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(event.datetime_start));

  const getCampusTag = (place: string) => {
    const lower = place.toLowerCase();
    if (lower.includes('453') || lower.includes('cyt') || lower.includes('tecnolog')) return 'ED. 453 · CYT';
    if (lower.includes('411') || lower.includes('minas')) return 'ED. 411 · MINAS';
    if (lower.includes('401') || lower.includes('ingenier')) return 'ED. 401 · ING';
    if (lower.includes('102') || lower.includes('ché') || lower.includes('che') || lower.includes('biblioteca')) return 'ED. 102 · PLAZA CHÉ';
    if (lower.includes('224') || lower.includes('freud')) return 'ED. 224 · FREUD';
    if (lower.includes('101') || lower.includes('león') || lower.includes('leon')) return 'ED. 101 · EL LEÓN';
    const match = place.match(/Ed(?:ificio|\.)?\s*(\d+)/i);
    if (match) return `ED. ${match[1]} · CAMPUS`;
    return place.length > 18 ? `${place.slice(0, 15).toUpperCase()}…` : place.toUpperCase();
  };

  const isEventToday = (datetimeStr: string) => {
    try {
      const evDate = new Date(datetimeStr);
      const today = new Date();
      return (
        evDate.getDate() === today.getDate() &&
        evDate.getMonth() === today.getMonth() &&
        evDate.getFullYear() === today.getFullYear()
      );
    } catch {
      return false;
    }
  };

  const statusColorMap = {
    PRO: 'bg-[#00D592]/15 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    ECU: 'bg-emerald-500/20 text-emerald-800 border border-emerald-500/30 dark:text-emerald-300',
    CAN: 'bg-red-500/15 text-red-700 border border-red-500/30 dark:text-red-300',
    FIN: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700',
  };

  return (
    <motion.article
      variants={staggerItem}
      whileHover={{ y: -5 }}
      transition={{ type: 'spring', duration: 0.35, bounce: 0 }}
      className="group relative flex flex-col overflow-hidden rounded-3xl border border-black/[0.08] dark:border-white/[0.08] bg-white/80 dark:bg-[#0E1720]/80 backdrop-blur-xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgb(0,0,0,0.25)] hover:border-[#00D592]/50 hover:shadow-[0_12px_36px_rgba(0,213,146,0.12)] transition-all duration-300"
    >
      {/* Imagen linkeada */}
      <Link
        to={`/events/${event.id}`}
        tabIndex={-1}
        aria-hidden="true"
        className="relative block aspect-16/9 w-full overflow-hidden bg-neutral-100 dark:bg-neutral-800"
      >
        {event.image ? (
          <img
            src={event.image}
            alt={`Fotografía del evento ${event.name}`}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-tr from-[#00D592]/10 via-[#00BA7C]/5 to-transparent text-[#00BA7C]">
            <span className="material-symbols-outlined text-5xl opacity-70" aria-hidden="true">
              event
            </span>
          </div>
        )}

        {/* Chips de estado y categoría superpuestos HeroUI Style */}
        <div className="absolute top-3.5 right-3.5 left-3.5 flex items-center justify-between gap-2">
          {event.category ? (
            <span className="rounded-full bg-black/60 dark:bg-black/75 px-3 py-1 font-semibold text-xs text-white backdrop-blur-md border border-white/10 shadow-xs">
              {EVENT_CATEGORY_LABELS[event.category] || event.category}
            </span>
          ) : (
            <div />
          )}

          <div className="flex items-center gap-1.5">
            {isEventToday(event.datetime_start) && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-black/60 dark:bg-black/75 px-2.5 py-1 text-[11px] font-semibold text-white backdrop-blur-md border border-white/10 shadow-xs">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#00D592] opacity-80" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-[#00D592]" />
                </span>
                <span>Hoy</span>
              </span>
            )}
            <span
              className={`rounded-full px-3 py-1 font-bold text-xs backdrop-blur-md shadow-xs ${
                statusColorMap[event.status] || 'bg-gray-100 text-gray-800'
              }`}
            >
              {EVENT_STATUS_LABELS[event.status] || event.status}
            </span>
          </div>
        </div>
      </Link>

      {/* Contenido */}
      <div className="flex flex-1 flex-col p-6">
        <div className="flex items-center justify-between text-xs">
          <span className="font-mono text-[10px] font-bold tracking-wider text-[#00875A] dark:text-[#00F5A0] bg-[#00D592]/10 dark:bg-[#00D592]/15 px-2.5 py-0.5 rounded-md border border-[#00D592]/20">
            {getCampusTag(event.place)}
          </span>
          <div className="flex items-center gap-1 text-xs font-semibold text-neutral-600 dark:text-neutral-300">
            <span className="material-symbols-outlined text-sm text-[#00D592]" aria-hidden="true">
              schedule
            </span>
            <time dateTime={event.datetime_start} className="tabular-nums capitalize">
              {formattedDate}
            </time>
          </div>
        </div>

        {/* Título linkeado */}
        <h2 className="mt-3 line-clamp-2 min-w-0 font-display font-bold text-lg text-gray-900 group-hover:text-[#00BA7C] dark:text-white dark:group-hover:text-[#00F5A0] transition-colors">
          <Link
            to={`/events/${event.id}`}
            className="focus-visible:outline-2 focus-visible:outline-[#00D592]"
          >
            {event.name}
          </Link>
        </h2>

        {/* Lugar */}
        <div className="mt-2 flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
          <span className="material-symbols-outlined text-base text-[#00D592]" aria-hidden="true">
            location_on
          </span>
          <span className="truncate font-medium">{event.place}</span>
        </div>

        {/* Descripción corta */}
        <p className="mt-2.5 line-clamp-2 min-w-0 text-sm text-gray-500 dark:text-gray-400 leading-relaxed">
          {event.description}
        </p>

        {/* Footer con SubscriptionButton */}
        <div className="mt-auto pt-4 border-t border-black/[0.06] dark:border-white/[0.08] flex items-center justify-between">
          <Link
            to={`/events/${event.id}`}
            className="inline-flex items-center gap-1 font-bold text-xs text-[#00875A] hover:text-[#00BA7C] dark:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
          >
            Ver detalles
            <span className="material-symbols-outlined text-sm">arrow_forward</span>
          </Link>

          <SubscriptionButton
            eventId={event.id}
            eventName={event.name}
            eventStatus={event.status}
            published={event.published}
            organizerId={event.organizer?.id}
            size="sm"
          />
        </div>
      </div>
    </motion.article>
  );
}
