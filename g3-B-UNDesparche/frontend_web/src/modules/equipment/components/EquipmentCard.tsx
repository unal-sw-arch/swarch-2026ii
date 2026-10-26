import React from 'react';
import { Link } from 'react-router';
import { motion } from 'framer-motion';
import {
  FACULTY_LABELS,
  IMPLEMENT_CATEGORY_LABELS,
  IMPLEMENT_STATE_LABELS,
  ImplementItem,
} from '../../../types/equipment';
import { staggerItem } from '../../../shared/motion/motionTokens';

export interface EquipmentCardProps {
  implement: ImplementItem;
  reserveDisabled?: boolean;
}

export function EquipmentCard({
  implement,
  reserveDisabled = false,
}: EquipmentCardProps): React.JSX.Element {
  const isAvailable = implement.state === 'DIS';

  const stateColorMap = {
    DIS: 'bg-[#00D592]/20 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    RES: 'bg-amber-500/20 text-amber-800 border border-amber-500/30 dark:text-amber-300',
    PRE: 'bg-blue-500/20 text-blue-800 border border-blue-500/30 dark:text-blue-300',
    NDS: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300',
  };

  return (
    <motion.article
      variants={staggerItem}
      whileHover={{ y: -4 }}
      transition={{ type: 'spring', duration: 0.35, bounce: 0 }}
      className="heroui-card group flex flex-col overflow-hidden rounded-3xl"
    >
      {/* Imagen o Placeholder */}
      <Link
        to={`/equipment/${implement.id}`}
        tabIndex={-1}
        aria-hidden="true"
        className="relative block aspect-4/3 w-full overflow-hidden bg-gray-100 dark:bg-gray-800"
      >
        {implement.image ? (
          <img
            src={implement.image}
            alt={`Fotografía de ${implement.name}`}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-tr from-[#00D592]/10 via-[#00BA7C]/5 to-transparent text-[#00BA7C]">
            <span className="material-symbols-outlined text-5xl opacity-75" aria-hidden="true">
              sports_soccer
            </span>
          </div>
        )}

        {/* Chips superpuestos: Estado y Facultad */}
        <div className="absolute top-3 right-3 left-3 flex items-center justify-between gap-1.5">
          <span className="max-w-[140px] truncate rounded-full bg-black/55 px-3 py-1 text-[11px] font-semibold text-white backdrop-blur-md border border-white/10 shadow-xs">
            {FACULTY_LABELS[implement.faculty] || implement.faculty}
          </span>

          <span
            className={`rounded-full px-3 py-1 text-[11px] font-bold backdrop-blur-md shadow-xs ${
              stateColorMap[implement.state] || 'bg-gray-100 text-gray-800'
            }`}
          >
            {IMPLEMENT_STATE_LABELS[implement.state] || implement.state}
          </span>
        </div>
      </Link>

      {/* Contenido */}
      <div className="flex flex-1 flex-col p-5">
        {implement.category && (
          <span className="text-[11px] font-bold uppercase tracking-wider text-[#00875A] dark:text-[#00F5A0]">
            {IMPLEMENT_CATEGORY_LABELS[implement.category] || implement.category}
          </span>
        )}

        {/* Título linkeado con Varela Round */}
        <h2 className="mt-1 line-clamp-1 min-w-0 font-display font-bold text-base text-gray-900 group-hover:text-[#00BA7C] dark:text-white dark:group-hover:text-[#00F5A0] transition-colors">
          <Link
            to={`/equipment/${implement.id}`}
            className="focus-visible:outline-2 focus-visible:outline-[#00D592]"
          >
            {implement.name}
          </Link>
        </h2>

        {/* Descripción corta */}
        <p className="mt-1 line-clamp-2 min-w-0 text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
          {implement.description}
        </p>

        {/* Acciones HeroUI */}
        <div className="mt-auto pt-4 border-t border-[#00D592]/15 dark:border-gray-800 flex items-center justify-between gap-2">
          <Link
            to={`/equipment/${implement.id}`}
            className="font-bold text-xs text-gray-600 hover:text-[#00BA7C] dark:text-gray-300 dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
          >
            Detalles
          </Link>

          {isAvailable ? (
            reserveDisabled ? (
              <button
                type="button"
                disabled
                title="No puedes reservar debido a una sanción activa en tu perfil"
                className="rounded-full bg-gray-100 px-3.5 py-1.5 text-xs font-semibold text-gray-400 opacity-70 cursor-not-allowed dark:bg-gray-800"
              >
                Sancionado
              </button>
            ) : (
              <Link
                to={`/equipment/${implement.id}/reserve`}
                className="heroui-btn-solid inline-flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold focus-visible:outline-2 focus-visible:outline-[#00D592]"
              >
                <span className="material-symbols-outlined text-sm" aria-hidden="true">
                  timer
                </span>
                Reservar (10 min)
              </Link>
            )
          ) : (
            <button
              type="button"
              disabled
              className="rounded-full bg-gray-100 px-3.5 py-1.5 text-xs font-medium text-gray-500 cursor-not-allowed dark:bg-gray-800 dark:text-gray-400"
            >
              {IMPLEMENT_STATE_LABELS[implement.state]}
            </button>
          )}
        </div>
      </div>
    </motion.article>
  );
}
