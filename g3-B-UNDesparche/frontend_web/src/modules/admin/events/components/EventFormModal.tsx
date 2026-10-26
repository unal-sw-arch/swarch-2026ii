import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { APIProvider, Map, Marker } from '@vis.gl/react-google-maps';
import {
  CampusEvent,
  CreateEventPayload,
  EVENT_CATEGORY_LABELS,
  EVENT_STATUS_LABELS,
  EventCategory,
  EventStatus,
  UpdateEventPayload,
} from '../../../../types/event';
import { modalBackdropVariants, modalDialogVariants } from '../../../../shared/motion/motionTokens';

export interface EventFormModalProps {
  open: boolean;
  eventToEdit?: CampusEvent | null;
  onClose: () => void;
  onSubmit: (payload: CreateEventPayload | UpdateEventPayload) => Promise<void>;
  saving?: boolean;
}

export function EventFormModal({
  open,
  eventToEdit,
  onClose,
  onSubmit,
  saving = false,
}: EventFormModalProps): React.JSX.Element | null {
  const isEditing = Boolean(eventToEdit);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [place, setPlace] = useState('');
  const [latitude, setLatitude] = useState<number>(4.638);
  const [longitude, setLongitude] = useState<number>(-74.0835);
  const [datetimeStart, setDatetimeStart] = useState('');
  const [datetimeEnd, setDatetimeEnd] = useState('');
  const [status, setStatus] = useState<EventStatus>('PRO');
  const [category, setCategory] = useState<EventCategory | ''>('');
  const [imageFile, setImageFile] = useState<File | null>(null);

  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setValidationErrors({});
      setSubmitError(null);
      setImageFile(null);

      if (eventToEdit) {
        setName(eventToEdit.name);
        setDescription(eventToEdit.description);
        setPlace(eventToEdit.place);
        setLatitude(typeof eventToEdit.latitude === 'number' ? eventToEdit.latitude : parseFloat(String(eventToEdit.latitude)) || 4.638);
        setLongitude(typeof eventToEdit.longitude === 'number' ? eventToEdit.longitude : parseFloat(String(eventToEdit.longitude)) || -74.0835);

        // Formatear a formato local para datetime-local (YYYY-MM-DDTHH:mm)
        try {
          const startIso = new Date(eventToEdit.datetime_start).toISOString().slice(0, 16);
          const endIso = new Date(eventToEdit.datetime_end).toISOString().slice(0, 16);
          setDatetimeStart(startIso);
          setDatetimeEnd(endIso);
        } catch {
          setDatetimeStart('');
          setDatetimeEnd('');
        }

        setStatus(eventToEdit.status);
        setCategory(eventToEdit.category || '');
      } else {
        // Valores por defecto para nuevo evento
        setName('');
        setDescription('');
        setPlace('');
        setLatitude(4.638);
        setLongitude(-74.0835);

        const now = new Date();
        now.setHours(now.getHours() + 1, 0, 0, 0);
        const nextHourIso = now.toISOString().slice(0, 16);
        const endHour = new Date(now.getTime() + 2 * 60 * 60 * 1000).toISOString().slice(0, 16);

        setDatetimeStart(nextHourIso);
        setDatetimeEnd(endHour);
        setStatus('PRO');
        setCategory('');
      }
    }
  }, [open, eventToEdit]);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !saving) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose, saving]);

  if (!open) return null;

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    if (!name.trim()) errors.name = 'El nombre del evento es obligatorio.';
    if (!description.trim()) errors.description = 'La descripción es obligatoria.';
    if (!place.trim()) errors.place = 'El lugar del evento es obligatorio.';

    if (isNaN(latitude) || latitude < -90 || latitude > 90) {
      errors.latitude = 'Latitud inválida (debe estar entre -90 y 90).';
    }
    if (isNaN(longitude) || longitude < -180 || longitude > 180) {
      errors.longitude = 'Longitud inválida (debe estar entre -180 y 180).';
    }

    if (!datetimeStart) {
      errors.datetimeStart = 'La fecha y hora de inicio son obligatorias.';
    } else if (!isEditing) {
      const startDate = new Date(datetimeStart);
      if (startDate.getTime() < Date.now() - 5 * 60 * 1000) {
        errors.datetimeStart = 'La fecha de inicio no puede estar en el pasado.';
      }
    }

    if (!datetimeEnd) {
      errors.datetimeEnd = 'La fecha y hora de fin son obligatorias.';
    } else if (datetimeStart && new Date(datetimeEnd) <= new Date(datetimeStart)) {
      errors.datetimeEnd = 'La fecha de fin debe ser posterior a la de inicio.';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || saving) return;

    // Convertir fechas a formato ISO con offset
    const startDateObj = new Date(datetimeStart);
    const endDateObj = new Date(datetimeEnd);

    const payload: CreateEventPayload | UpdateEventPayload = {
      name: name.trim(),
      description: description.trim(),
      place: place.trim(),
      latitude: parseFloat(Number(latitude).toFixed(6)),
      longitude: parseFloat(Number(longitude).toFixed(6)),
      datetime_start: startDateObj.toISOString(),
      datetime_end: endDateObj.toISOString(),
      status,
      category: category ? (category as EventCategory) : null,
      image_file: imageFile,
    };

    try {
      setSubmitError(null);
      await onSubmit(payload);
      onClose();
    } catch (err: any) {
      setSubmitError(err?.message || 'Error al guardar el evento. Verifica tus permisos y los datos ingresados.');
    }
  };

  if (typeof document === 'undefined') return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div role="presentation" className="fixed inset-0 z-[100] flex items-center justify-center p-2.5 sm:p-4">
          {/* Backdrop */}
          <motion.div
            variants={modalBackdropVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
            onClick={saving ? undefined : onClose}
            aria-hidden="true"
          />

          {/* Modal Dialog */}
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-labelledby="event-modal-title"
            variants={modalDialogVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 max-h-[92dvh] w-full max-w-2xl overflow-y-auto rounded-[var(--radius-undesparche)] bg-white p-4 sm:p-6 shadow-2xl dark:bg-gray-900 border border-black/5 dark:border-white/10"
          >
        <div className="flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <div>
            <h2 id="event-modal-title" className="font-display font-bold text-xl text-gray-900 dark:text-white">
              {isEditing ? 'Editar Evento' : 'Crear Nuevo Evento'}
            </h2>
            <p className="text-xs text-gray-500">
              {isEditing ? 'Actualiza los datos del evento.' : 'Publica una nueva actividad para la comunidad UNAL.'}
            </p>
          </div>
          <button
            type="button"
            disabled={saving}
            onClick={onClose}
            aria-label="Cerrar formulario"
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800"
          >
            <span className="material-symbols-outlined text-xl" aria-hidden="true">
              close
            </span>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          {submitError && (
            <div className="flex items-start gap-2.5 rounded-[var(--radius-undesparche)] border border-red-200 bg-red-50 p-3 text-xs text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
              <span className="material-symbols-outlined text-base text-red-500 shrink-0 mt-0.5" aria-hidden="true">
                error
              </span>
              <span>{submitError}</span>
            </div>
          )}

          {/* Nombre */}
          <div>
            <label htmlFor="event-name" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Nombre del evento *
            </label>
            <input
              id="event-name"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ej: Asamblea Triestamentaria, Torneo de Futsal…"
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            />
            {validationErrors.name && (
              <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{validationErrors.name}</p>
            )}
          </div>

          {/* Categoría y Estado */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="event-category" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                Categoría
              </label>
              <select
                id="event-category"
                value={category}
                onChange={(e) => setCategory(e.target.value as EventCategory | '')}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              >
                <option value="">Sin categoría</option>
                {Object.entries(EVENT_CATEGORY_LABELS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="event-status" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                Estado *
              </label>
              <select
                id="event-status"
                value={status}
                onChange={(e) => setStatus(e.target.value as EventStatus)}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              >
                {Object.entries(EVENT_STATUS_LABELS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Fechas de inicio y fin */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="event-start" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                Fecha y hora de inicio *
              </label>
              <input
                id="event-start"
                type="datetime-local"
                required
                value={datetimeStart}
                onChange={(e) => setDatetimeStart(e.target.value)}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              />
              {validationErrors.datetimeStart && (
                <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{validationErrors.datetimeStart}</p>
              )}
            </div>

            <div>
              <label htmlFor="event-end" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                Fecha y hora de fin *
              </label>
              <input
                id="event-end"
                type="datetime-local"
                required
                value={datetimeEnd}
                onChange={(e) => setDatetimeEnd(e.target.value)}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              />
              {validationErrors.datetimeEnd && (
                <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{validationErrors.datetimeEnd}</p>
              )}
            </div>
          </div>

          {/* Lugar */}
          <div>
            <label htmlFor="event-place" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Lugar / Edificio del campus *
            </label>
            <input
              id="event-place"
              type="text"
              required
              value={place}
              onChange={(e) => setPlace(e.target.value)}
              placeholder="Ej: Polideportivo, Plaza Santander, Salón 201 CyT…"
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            />
            {validationErrors.place && (
              <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{validationErrors.place}</p>
            )}
          </div>

          {/* Ubicación en el mapa */}
          <div>
            <p id="event-map-hint" className="mb-1 text-xs font-semibold text-gray-700 dark:text-gray-300">
              Ubicación en el mapa — arrastra el marcador para ajustar las coordenadas *
            </p>
            <div
              className="h-64 sm:h-72 w-full overflow-hidden rounded-[var(--radius-undesparche)] border border-gray-300 dark:border-gray-700 shadow-2xs"
              role="group"
              aria-describedby="event-map-hint"
            >
              <APIProvider apiKey={import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? ''}>
                <Map
                  defaultCenter={{ lat: latitude, lng: longitude }}
                  center={{ lat: latitude, lng: longitude }}
                  defaultZoom={15}
                  disableDefaultUI
                  gestureHandling="greedy"
                  style={{ width: '100%', height: '100%' }}
                  onClick={(e) => {
                    if (e.detail.latLng) {
                      setLatitude(parseFloat(e.detail.latLng.lat.toFixed(6)));
                      setLongitude(parseFloat(e.detail.latLng.lng.toFixed(6)));
                    }
                  }}
                >
                  <Marker
                    position={{ lat: latitude, lng: longitude }}
                    draggable
                    onDragEnd={(e) => {
                      if (e.latLng) {
                        setLatitude(parseFloat(e.latLng.lat().toFixed(6)));
                        setLongitude(parseFloat(e.latLng.lng().toFixed(6)));
                      }
                    }}
                  />
                </Map>
              </APIProvider>
            </div>
            <p className="mt-1 block text-xs text-gray-500 dark:text-gray-400 tabular-nums">
              {latitude.toFixed(6)}, {longitude.toFixed(6)}
            </p>
            {(validationErrors.latitude || validationErrors.longitude) && (
              <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">
                {validationErrors.latitude || validationErrors.longitude}
              </p>
            )}
          </div>

          {/* Fotografía del evento */}
          <div>
            <label htmlFor="event-image" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Fotografía / Banner (Opcional)
            </label>
            <input
              id="event-image"
              type="file"
              accept="image/*"
              onChange={(e) => setImageFile(e.target.files?.[0] || null)}
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-xs shadow-2xs file:mr-3 file:rounded-md file:border-0 file:bg-[var(--color-undesparche-primary)] file:px-2.5 file:py-1 file:font-semibold file:text-xs file:text-white dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
            />
          </div>

          {/* Descripción */}
          <div>
            <label htmlFor="event-desc" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Descripción detallada *
            </label>
            <textarea
              id="event-desc"
              required
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Explica de qué trata la actividad, cupos, requerimientos, orden del día…"
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            />
            {validationErrors.description && (
              <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{validationErrors.description}</p>
            )}
          </div>

          {/* Botones de acción */}
          <div className="flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-end gap-2.5 sm:gap-3 pt-4 border-t border-gray-100 dark:border-gray-800">
            <button
              type="button"
              disabled={saving}
              onClick={onClose}
              className="w-full sm:w-auto text-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-4 py-2 font-medium text-xs text-gray-700 shadow-xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-[var(--radius-undesparche)] bg-[var(--color-undesparche-primary)] px-5 py-2 font-bold text-xs text-white shadow-xs hover:bg-[var(--color-undesparche-primary-dark)] focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:opacity-50"
            >
              {saving && (
                <span className="material-symbols-outlined animate-spin text-sm" aria-hidden="true">
                  progress_activity
                </span>
              )}
              {isEditing ? 'Guardar Cambios' : 'Crear Evento'}
            </button>
          </div>
        </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
