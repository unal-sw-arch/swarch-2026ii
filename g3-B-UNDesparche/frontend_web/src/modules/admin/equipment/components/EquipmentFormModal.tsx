import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  CreateImplementPayload,
  FACULTY_LABELS,
  FacultyCode,
  IMPLEMENT_CATEGORY_LABELS,
  ImplementCategory,
  ImplementItem,
  UpdateImplementPayload,
} from '../../../../types/equipment';
import { modalBackdropVariants, modalDialogVariants } from '../../../../shared/motion/motionTokens';

export interface EquipmentFormModalProps {
  open: boolean;
  implementToEdit?: ImplementItem | null;
  isSystemAdmin?: boolean;
  defaultFaculty?: FacultyCode | null;
  onClose: () => void;
  onSubmit: (payload: CreateImplementPayload | UpdateImplementPayload) => Promise<void>;
  saving?: boolean;
}

export function EquipmentFormModal({
  open,
  implementToEdit,
  isSystemAdmin = false,
  defaultFaculty,
  onClose,
  onSubmit,
  saving = false,
}: EquipmentFormModalProps): React.JSX.Element | null {
  const isEditing = Boolean(implementToEdit);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<ImplementCategory>('');
  const [state, setState] = useState<'DIS' | 'NDS'>('DIS');
  const [faculty, setFaculty] = useState<FacultyCode | ''>('');
  const [imageFile, setImageFile] = useState<File | null>(null);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isReservedOrBorrowed =
    implementToEdit && (implementToEdit.state === 'RES' || implementToEdit.state === 'PRE');

  useEffect(() => {
    if (open) {
      setErrors({});
      setSubmitError(null);
      setImageFile(null);

      if (implementToEdit) {
        setName(implementToEdit.name);
        setDescription(implementToEdit.description);
        setCategory(implementToEdit.category || '');
        setState(implementToEdit.state === 'NDS' ? 'NDS' : 'DIS');
        setFaculty(implementToEdit.faculty);
      } else {
        setName('');
        setDescription('');
        setCategory('');
        setState('DIS');
        setFaculty(isSystemAdmin ? 'ING' : defaultFaculty || 'ING');
      }
    }
  }, [open, implementToEdit, isSystemAdmin, defaultFaculty]);

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

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'El nombre del implemento es obligatorio.';
    if (!description.trim()) errs.description = 'La descripción es obligatoria.';
    if (isSystemAdmin && !faculty) errs.faculty = 'Debes asignar la facultad responsable.';

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || saving) return;

    const payload: CreateImplementPayload | UpdateImplementPayload = {
      name: name.trim(),
      description: description.trim(),
      category,
      state: isReservedOrBorrowed ? undefined : state,
      faculty: isSystemAdmin && faculty ? (faculty as FacultyCode) : undefined,
      image_file: imageFile,
    };

    try {
      setSubmitError(null);
      await onSubmit(payload);
      onClose();
    } catch (err: any) {
      setSubmitError(err?.message || 'Error al guardar el implemento.');
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
            aria-labelledby="equipment-modal-title"
            variants={modalDialogVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-[var(--radius-undesparche)] bg-white p-4 sm:p-6 shadow-2xl dark:bg-gray-900 border border-black/5 dark:border-white/10"
          >
        <div className="flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <div>
            <h2 id="equipment-modal-title" className="font-display font-bold text-xl text-gray-900 dark:text-white">
              {isEditing ? 'Editar Implemento' : 'Nuevo Implemento'}
            </h2>
            <p className="text-xs text-gray-500">
              Registra el material deportivo o recreativo para el catálogo de la facultad.
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

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
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
            <label htmlFor="equip-name" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Nombre del implemento *
            </label>
            <input
              id="equip-name"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ej: Balón de Baloncesto Spalding #7, Raqueta Wilson…"
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            />
            {errors.name && <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{errors.name}</p>}
          </div>

          {/* Categoría */}
          <div>
            <label htmlFor="equip-category" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Categoría
            </label>
            <select
              id="equip-category"
              value={category}
              onChange={(e) => setCategory(e.target.value as ImplementCategory)}
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            >
              <option value="">Sin categoría</option>
              {Object.entries(IMPLEMENT_CATEGORY_LABELS).map(([k, label]) => {
                if (!k) return null;
                return (
                  <option key={k} value={k}>
                    {label}
                  </option>
                );
              })}
            </select>
          </div>

          {/* Facultad (Solo si es Administrador del Sistema) */}
          {isSystemAdmin && (
            <div>
              <label htmlFor="equip-faculty" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                Facultad Asignada *
              </label>
              <select
                id="equip-faculty"
                required
                value={faculty}
                onChange={(e) => setFaculty(e.target.value as FacultyCode)}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              >
                {Object.entries(FACULTY_LABELS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
              {errors.faculty && <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{errors.faculty}</p>}
            </div>
          )}

          {/* Estado (solo DIS o NDS; bloqueado si RES o PRE) */}
          <div>
            <label htmlFor="equip-state" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Disponibilidad Inicial
            </label>
            {isReservedOrBorrowed ? (
              <div className="mt-1 rounded-md bg-amber-50 p-2 text-xs text-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                El implemento se encuentra actualmente en estado <strong>{implementToEdit?.state}</strong> (Reservado o Prestado). Su estado no puede alterarse manualmente hasta que se devuelva o cancele la reserva.
              </div>
            ) : (
              <select
                id="equip-state"
                value={state}
                onChange={(e) => setState(e.target.value as 'DIS' | 'NDS')}
                className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
              >
                <option value="DIS">Disponible (DIS)</option>
                <option value="NDS">No Disponible (NDS)</option>
              </select>
            )}
          </div>

          {/* Fotografía */}
          <div>
            <label htmlFor="equip-image" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Fotografía (Opcional)
            </label>
            <input
              id="equip-image"
              type="file"
              accept="image/*"
              onChange={(e) => setImageFile(e.target.files?.[0] || null)}
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-xs shadow-2xs file:mr-3 file:rounded-md file:border-0 file:bg-[var(--color-undesparche-primary)] file:px-2.5 file:py-1 file:font-semibold file:text-xs file:text-white dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
            />
          </div>

          {/* Descripción */}
          <div>
            <label htmlFor="equip-desc" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
              Descripción y estado físico *
            </label>
            <textarea
              id="equip-desc"
              required
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Indica el estado físico, marca, cantidad o recomendaciones de uso…"
              className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
            />
            {errors.description && (
              <p className="mt-1 text-xs text-[var(--color-undesparche-danger)]">{errors.description}</p>
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
              {isEditing ? 'Guardar Cambios' : 'Crear Implemento'}
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
