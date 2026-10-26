import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { modalBackdropVariants, modalDialogVariants } from '../../../shared/motion/motionTokens';

import { useToast } from '../../../shared/context/ToastContext';

export interface GuestSubscriptionDialogProps {
  open: boolean;
  eventName: string;
  onClose: () => void;
  onSubmit: (email: string) => Promise<void>;
}

export function GuestSubscriptionDialog({
  open,
  eventName,
  onClose,
  onSubmit,
}: GuestSubscriptionDialogProps): React.JSX.Element {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const toast = useToast();

  useEffect(() => {
    if (open) {
      setEmail('');
      setError(null);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !submitting) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose, submitting]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(email.trim());
      toast.success(
        '¡Suscripción confirmada!',
        `Te hemos registrado a "${eventName}". Te llegará un recordatorio antes del inicio.`
      );
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al registrar la suscripción');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <div role="presentation" className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4">
          {/* Backdrop */}
          <motion.div
            variants={modalBackdropVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
            onClick={submitting ? undefined : onClose}
            aria-hidden="true"
          />

          {/* Modal Dialog */}
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-labelledby="guest-dialog-title"
            variants={modalDialogVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 w-full max-w-md rounded-[var(--radius-undesparche)] bg-white p-4 sm:p-6 shadow-2xl dark:bg-gray-900 border border-black/5 dark:border-white/10"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[var(--color-undesparche-primary)] dark:bg-emerald-950/60">
                <span className="material-symbols-outlined text-2xl" aria-hidden="true">
                  mail
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <h2 id="guest-dialog-title" className="font-display font-semibold text-lg text-gray-900 dark:text-gray-100">
                  Suscripción a evento
                </h2>
                <p className="line-clamp-1 text-xs text-gray-500">
                  {eventName}
                </p>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="mt-5 space-y-4">
              <div>
                <label
                  htmlFor="guest-email-input"
                  className="block font-medium text-xs text-gray-700 dark:text-gray-300"
                >
                  Correo electrónico
                </label>
                <input
                  ref={inputRef}
                  id="guest-email-input"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="tu_correo@unal.edu.co"
                  className="mt-1 w-full rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-3 py-2 text-sm shadow-2xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-white"
                />
                <p className="mt-1 text-[11px] text-gray-500">
                  Te enviaremos notificaciones sobre cambios o recordatorios de esta actividad.
                </p>
              </div>

              {error && (
                <p role="alert" className="text-xs font-medium text-[var(--color-undesparche-danger)]">
                  {error}
                </p>
              )}

              <div className="mt-6 flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-end gap-2.5 sm:gap-3">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={onClose}
                  className="w-full sm:w-auto text-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-4 py-2 font-medium text-sm text-gray-700 shadow-xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-[var(--radius-undesparche)] bg-[var(--color-undesparche-primary)] px-4 py-2 font-medium text-sm text-white shadow-xs hover:bg-[var(--color-undesparche-primary-dark)] focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:opacity-60"
                >
                  {submitting && (
                    <span className="material-symbols-outlined animate-spin text-sm" aria-hidden="true">
                      progress_activity
                    </span>
                  )}
                  Suscribirme
                </button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
