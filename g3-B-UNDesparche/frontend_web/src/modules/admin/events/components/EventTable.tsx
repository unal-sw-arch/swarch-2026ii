import React from 'react';
import {
  CampusEvent,
  EVENT_CATEGORY_LABELS,
  EVENT_STATUS_LABELS,
} from '../../../../types/event';

export interface EventTableProps {
  events: CampusEvent[];
  showOrganizer?: boolean;
  onEdit: (event: CampusEvent) => void;
  onDelete: (event: CampusEvent) => void;
  onPublish?: (event: CampusEvent) => void;
  publishingId?: number | null;
}

export function EventTable({
  events,
  showOrganizer = false,
  onEdit,
  onDelete,
  onPublish,
  publishingId,
}: EventTableProps): React.JSX.Element {
  const statusColorMap = {
    PRO: 'bg-[#00D592]/20 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    ECU: 'bg-emerald-500/20 text-emerald-800 border border-emerald-500/30 dark:text-emerald-300',
    CAN: 'bg-red-500/20 text-red-700 border border-red-500/30 dark:text-red-300',
    FIN: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300',
  };

  if (events.length === 0) {
    return (
      <div className="heroui-card p-8 text-center text-gray-500 dark:text-gray-400">
        <span className="material-symbols-outlined text-4xl text-gray-400 mb-2">event_busy</span>
        <p className="font-bold text-sm">No se encontraron eventos registrados.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 1. VISTA MÓVIL (TARJETAS RESPONSIVAS MD:HIDDEN) */}
      <div className="grid grid-cols-1 gap-3.5 md:hidden">
        {events.map((event) => {
          const startDateFormatted = new Intl.DateTimeFormat('es-CO', {
            day: 'numeric',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
          }).format(new Date(event.datetime_start));

          const isFinishedOrCancelled = event.status === 'FIN' || event.status === 'CAN';
          const isLockedForEdit = event.published && isFinishedOrCancelled;

          return (
            <div
              key={event.id}
              className="heroui-card p-4 space-y-3 border border-[#00D592]/15 shadow-sm"
            >
              {/* Header de la tarjeta */}
              <div className="flex items-start gap-3">
                <div className="h-11 w-11 shrink-0 overflow-hidden rounded-2xl bg-gray-100 dark:bg-gray-800 border border-[#00D592]/20">
                  {event.image ? (
                    <img
                      src={event.image}
                      alt=""
                      aria-hidden="true"
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-[#00BA7C]">
                      <span className="material-symbols-outlined text-xl">event</span>
                    </div>
                  )}
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-1">
                    <span className="heroui-chip text-[10px] py-0.5 px-2">
                      {event.category ? EVENT_CATEGORY_LABELS[event.category] : 'Sin categoría'}
                    </span>
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 font-bold text-[10px] ${
                        statusColorMap[event.status] || 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {EVENT_STATUS_LABELS[event.status] || event.status}
                    </span>
                  </div>

                  <h3 className="mt-1 font-display font-bold text-sm text-gray-900 dark:text-white line-clamp-1">
                    {event.name}
                  </h3>
                </div>
              </div>

              {/* Detalles clave */}
              <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300 border-y border-[#00D592]/10 py-2.5">
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-sm text-[#00D592]">schedule</span>
                  <span className="tabular-nums font-semibold">{startDateFormatted}</span>
                  <span className="mx-1 text-gray-400">·</span>
                  {event.published ? (
                    <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-600 dark:text-emerald-400">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                      Publicado
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-[11px] font-bold text-amber-600 dark:text-amber-400">
                      <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                      Borrador
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-sm text-[#00D592]">location_on</span>
                  <span className="truncate">{event.place}</span>
                </div>

                {showOrganizer && event.organizer && (
                  <div className="flex items-center gap-1.5 text-gray-500">
                    <span className="material-symbols-outlined text-sm">person</span>
                    <span className="truncate">{event.organizer.name} ({event.organizer.email})</span>
                  </div>
                )}
              </div>

              {/* Botones de acción táctiles */}
              <div className="flex items-center justify-end gap-2 pt-1">
                {!event.published && onPublish && !isFinishedOrCancelled && (
                  <button
                    type="button"
                    disabled={publishingId === event.id}
                    onClick={() => onPublish(event)}
                    className="heroui-btn-solid px-3.5 py-1.5 text-xs font-bold"
                  >
                    {publishingId === event.id ? 'Publicando…' : 'Publicar'}
                  </button>
                )}

                <button
                  type="button"
                  disabled={isLockedForEdit}
                  onClick={() => onEdit(event)}
                  className="heroui-btn-flat px-3.5 py-1.5 text-xs font-bold inline-flex items-center gap-1"
                >
                  <span className="material-symbols-outlined text-sm">edit</span>
                  <span>Editar</span>
                </button>

                <button
                  type="button"
                  onClick={() => onDelete(event)}
                  className="rounded-full bg-red-500/10 text-red-600 border border-red-500/20 px-3 py-1.5 text-xs font-bold inline-flex items-center gap-1 hover:bg-red-500/20 transition-colors"
                >
                  <span className="material-symbols-outlined text-sm">delete</span>
                  <span>Eliminar</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* 2. VISTA ESCRITORIO (TABLA HIDDEN MD:BLOCK) */}
      <div className="hidden md:block overflow-x-auto heroui-card p-0 overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200 text-left text-xs dark:divide-gray-800">
          <thead className="bg-gray-50/80 text-gray-600 dark:bg-[#111A24] dark:text-gray-300">
            <tr>
              <th scope="col" className="px-4 py-3.5 font-bold">
                Evento
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Estado
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Visibilidad
              </th>
              {showOrganizer && (
                <th scope="col" className="px-3 py-3.5 font-bold">
                  Organizador
                </th>
              )}
              <th scope="col" className="px-3 py-3.5 font-bold">
                Fecha y Hora
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Lugar
              </th>
              <th scope="col" className="px-4 py-3.5 text-right font-bold">
                Acciones
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-800/80">
            {events.map((event) => {
              const startDateFormatted = new Intl.DateTimeFormat('es-CO', {
                day: 'numeric',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              }).format(new Date(event.datetime_start));

              const isFinishedOrCancelled = event.status === 'FIN' || event.status === 'CAN';
              const isLockedForEdit = event.published && isFinishedOrCancelled;

              return (
                <tr key={event.id} className="hover:bg-gray-50/70 dark:hover:bg-[#14202C]/50 transition-colors">
                  {/* Evento con miniatura */}
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 shrink-0 overflow-hidden rounded-xl bg-gray-100 dark:bg-gray-800 border border-[#00D592]/20">
                        {event.image ? (
                          <img
                            src={event.image}
                            alt=""
                            aria-hidden="true"
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center text-[#00BA7C]">
                            <span className="material-symbols-outlined text-lg" aria-hidden="true">
                              event
                            </span>
                          </div>
                        )}
                      </div>
                      <div className="min-w-0 max-w-xs">
                        <p className="truncate font-bold text-sm text-gray-900 dark:text-white">
                          {event.name}
                        </p>
                        <p className="truncate text-xs text-gray-500 dark:text-gray-400">
                          {event.category ? EVENT_CATEGORY_LABELS[event.category] : 'Sin categoría'}
                        </p>
                      </div>
                    </div>
                  </td>

                  {/* Estado */}
                  <td className="px-3 py-3">
                    <span
                      className={`inline-block rounded-full px-2.5 py-0.5 font-bold text-[10px] ${
                        statusColorMap[event.status] || 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {EVENT_STATUS_LABELS[event.status] || event.status}
                    </span>
                  </td>

                  {/* Visibilidad (Publicado / Borrador) */}
                  <td className="px-3 py-3">
                    {event.published ? (
                      <span className="inline-flex items-center gap-1 font-bold text-[11px] text-emerald-600 dark:text-emerald-400">
                        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                        Publicado
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 font-bold text-[11px] text-amber-600 dark:text-amber-400">
                        <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                        Borrador
                      </span>
                    )}
                  </td>

                  {/* Organizador */}
                  {showOrganizer && (
                    <td className="px-3 py-3 text-xs text-gray-700 dark:text-gray-300">
                      <p className="font-semibold truncate max-w-[140px]">
                        {event.organizer?.name || 'Sistema / Sin asignar'}
                      </p>
                      <p className="text-[10px] text-gray-400 truncate max-w-[140px]">
                        {event.organizer?.email || ''}
                      </p>
                    </td>
                  )}

                  {/* Fecha y Hora tabular */}
                  <td className="px-3 py-3 tabular-nums text-xs text-gray-700 dark:text-gray-300 whitespace-nowrap">
                    {startDateFormatted}
                  </td>

                  {/* Lugar */}
                  <td className="px-3 py-3 text-xs text-gray-600 dark:text-gray-400 max-w-[140px] truncate">
                    {event.place}
                  </td>

                  {/* Acciones */}
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      {/* Botón Publicar (si es borrador) */}
                      {!event.published && onPublish && !isFinishedOrCancelled && (
                        <button
                          type="button"
                          disabled={publishingId === event.id}
                          onClick={() => onPublish(event)}
                          aria-label={`Publicar ${event.name}`}
                          title="Publicar evento"
                          className="heroui-btn-solid px-2.5 py-1 text-[11px] font-bold"
                        >
                          {publishingId === event.id ? 'Publicando…' : 'Publicar'}
                        </button>
                      )}

                      {/* Botón Editar */}
                      <button
                        type="button"
                        disabled={isLockedForEdit}
                        onClick={() => onEdit(event)}
                        aria-label={`Editar ${event.name}`}
                        title={isLockedForEdit ? 'No editable (finalizado o cancelado)' : 'Editar evento'}
                        className="rounded-full p-1.5 text-gray-600 hover:bg-[#00D592]/15 hover:text-[#00BA7C] focus-visible:outline-2 focus-visible:outline-[#00D592] disabled:opacity-40 disabled:cursor-not-allowed dark:text-gray-400 dark:hover:bg-gray-800 transition-colors"
                      >
                        <span className="material-symbols-outlined text-lg" aria-hidden="true">
                          edit
                        </span>
                      </button>

                      {/* Botón Eliminar */}
                      <button
                        type="button"
                        onClick={() => onDelete(event)}
                        aria-label={`Eliminar ${event.name}`}
                        title="Eliminar evento"
                        className="rounded-full p-1.5 text-red-500 hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-[#00D592] dark:text-red-400 dark:hover:bg-red-950/40 transition-colors"
                      >
                        <span className="material-symbols-outlined text-lg" aria-hidden="true">
                          delete
                        </span>
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
