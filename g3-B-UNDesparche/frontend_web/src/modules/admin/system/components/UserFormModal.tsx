import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { BackendProfile, RoleName, UserStatus, UserUpdatePayload } from '../../../../types/auth';
import { FACULTY_LABELS, FacultyCode } from '../../../../types/equipment';
import { modalBackdropVariants, modalDialogVariants } from '../../../../shared/motion/motionTokens';

export interface UserFormModalProps {
  open: boolean;
  userToEdit: BackendProfile | null;
  onClose: () => void;
  onSubmit: (id: number, payload: UserUpdatePayload) => Promise<void>;
  saving?: boolean;
}

const AVAILABLE_ROLES: RoleName[] = [
  'Miembro de la Comunidad',
  'Administrador de Eventos',
  'Administrador de Implementos',
  'Administrador del Sistema',
];

export function UserFormModal({
  open,
  userToEdit,
  onClose,
  onSubmit,
  saving = false,
}: UserFormModalProps): React.JSX.Element | null {
  const [selectedRole, setSelectedRole] = useState<RoleName>('Miembro de la Comunidad');
  const [status, setStatus] = useState<UserStatus>('ACT');
  const [faculty, setFaculty] = useState<FacultyCode | ''>('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open && userToEdit) {
      setError(null);
      const role = userToEdit.roles?.[0] || 'Miembro de la Comunidad';
      setSelectedRole(role);
      setStatus(userToEdit.status || 'ACT');
      setFaculty((userToEdit.faculty as FacultyCode) || '');
    }
  }, [open, userToEdit]);

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

  if (!open || !userToEdit) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validación de negocio: si asigna Implementos, exige facultad
    if (selectedRole === 'Administrador de Implementos' && !faculty) {
      setError('Para el rol "Administrador de Implementos" es obligatorio asignar una facultad.');
      return;
    }

    const payload: UserUpdatePayload = {
      status,
      // Si es "Miembro de la Comunidad", enviamos [] para remover grupos en backend
      roles: selectedRole === 'Miembro de la Comunidad' ? [] : [selectedRole],
      faculty: faculty || null,
    };

    try {
      await onSubmit(userToEdit.id, payload);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al actualizar usuario');
    }
  };

  if (typeof document === 'undefined') return null;

  return createPortal(
    <AnimatePresence>
      {open && userToEdit && (
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
            aria-labelledby="user-modal-title"
            variants={modalDialogVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 w-full max-w-md max-h-[92dvh] overflow-y-auto rounded-[var(--radius-undesparche)] bg-white p-4 sm:p-6 shadow-2xl dark:bg-gray-900 border border-black/5 dark:border-white/10"
          >
            <div className="flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
              <div>
                <h2 id="user-modal-title" className="font-display font-bold text-lg text-gray-900 dark:text-white">
                  Gestionar Usuario
                </h2>
                <p className="text-xs text-gray-500">
                  {userToEdit.name} · {userToEdit.email}
                </p>
              </div>
              <button
                type="button"
                disabled={saving}
                onClick={onClose}
                aria-label="Cerrar modal"
                className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
              >
                <span className="material-symbols-outlined text-xl" aria-hidden="true">
                  close
                </span>
              </button>
            </div>

            {error && (
              <div className="mt-4 rounded-md bg-red-50 p-2.5 text-xs text-red-700">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="mt-5 space-y-4">
              {/* Asignación de Rol */}
              <div>
                <label htmlFor="user-role-select" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                  Rol del Usuario (Máximo 1 rol)
                </label>
                <select
                  id="user-role-select"
                  value={selectedRole}
                  onChange={(e) => setSelectedRole(e.target.value as RoleName)}
                  className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
                >
                  {AVAILABLE_ROLES.map((r) => (
                    <option key={r} value={r} className="text-gray-900 dark:text-white bg-white dark:bg-gray-800">
                      {r}
                    </option>
                  ))}
                </select>
              </div>

              {/* Facultad (exigida para Implementos) */}
              <div>
                <label htmlFor="user-faculty-select" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                  Facultad {selectedRole === 'Administrador de Implementos' ? '*' : '(Opcional)'}
                </label>
                <select
                  id="user-faculty-select"
                  value={faculty}
                  onChange={(e) => setFaculty(e.target.value as FacultyCode)}
                  className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
                >
                  <option value="" className="text-gray-900 dark:text-white bg-white dark:bg-gray-800">Sin facultad asignada</option>
                  {Object.entries(FACULTY_LABELS).map(([k, label]) => (
                    <option key={k} value={k} className="text-gray-900 dark:text-white bg-white dark:bg-gray-800">
                      {label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Estado del usuario: ACT o SAN */}
              <div>
                <label htmlFor="user-status-select" className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                  Estado Disciplinario
                </label>
                <select
                  id="user-status-select"
                  value={status}
                  onChange={(e) => setStatus(e.target.value as UserStatus)}
                  className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
                >
                  <option value="ACT" className="text-gray-900 dark:text-white bg-white dark:bg-gray-800">Activo (ACT) — Habilitado para reservas</option>
                  <option value="SAN" className="text-gray-900 dark:text-white bg-white dark:bg-gray-800">Sancionado (SAN) — Restringido de préstamos</option>
                </select>
              </div>

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
                  Actualizar Usuario
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
