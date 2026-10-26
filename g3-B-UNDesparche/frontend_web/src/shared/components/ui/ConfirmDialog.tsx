import React, { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { modalBackdropVariants, modalDialogVariants } from '../../motion/motionTokens';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  pending?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  danger = false,
  pending = false,
  onClose,
  onConfirm,
}: ConfirmDialogProps): React.JSX.Element {
  const dialogRef = useRef<HTMLDivElement>(null);
  const confirmBtnRef = useRef<HTMLButtonElement>(null);
  const shouldReduceMotion = useReducedMotion();

  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !pending) {
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    confirmBtnRef.current?.focus();

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onClose, pending]);

  if (typeof document === 'undefined') return <></>;

  return createPortal(
    <AnimatePresence>
      {open && (
        <div
          role="presentation"
          className="fixed inset-0 z-[100] flex items-center justify-center p-3 sm:p-4"
        >
          {/* Backdrop */}
          <motion.div
            variants={shouldReduceMotion ? undefined : modalBackdropVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
            onClick={pending ? undefined : onClose}
            aria-hidden="true"
          />

          {/* Modal Dialog */}
          <motion.div
            ref={dialogRef}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-dialog-title"
            aria-describedby={description ? 'confirm-dialog-desc' : undefined}
            variants={shouldReduceMotion ? undefined : modalDialogVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 w-full max-w-md overflow-hidden rounded-[var(--radius-undesparche)] bg-white p-4 sm:p-6 shadow-2xl dark:bg-gray-900 border border-black/5 dark:border-white/10"
          >
            <div className="flex items-start gap-3 sm:gap-4">
              <div
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                  danger
                    ? 'bg-red-100 text-red-600 dark:bg-red-950/60 dark:text-red-400'
                    : 'bg-emerald-100 text-[var(--color-undesparche-primary)] dark:bg-emerald-950/60'
                }`}
              >
                <span className="material-symbols-outlined text-2xl" aria-hidden="true">
                  {danger ? 'warning' : 'help'}
                </span>
              </div>

              <div className="flex-1 min-w-0">
                <h3
                  id="confirm-dialog-title"
                  className="font-display font-semibold text-lg text-gray-900 dark:text-gray-100"
                >
                  {title}
                </h3>
                {description && (
                  <p
                    id="confirm-dialog-desc"
                    className="mt-2 text-sm text-gray-600 dark:text-gray-400 break-words"
                  >
                    {description}
                  </p>
                )}
              </div>
            </div>

            <div className="mt-6 flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-end gap-2.5 sm:gap-3">
              <motion.button
                type="button"
                whileTap={{ scale: 0.97 }}
                disabled={pending}
                onClick={onClose}
                className="w-full sm:w-auto text-center rounded-[var(--radius-undesparche)] border border-gray-300 bg-white px-4 py-2 font-medium text-sm text-gray-700 shadow-xs hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700"
              >
                {cancelLabel}
              </motion.button>
              <motion.button
                ref={confirmBtnRef}
                type="button"
                whileTap={{ scale: 0.97 }}
                disabled={pending}
                onClick={onConfirm}
                className={`inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-[var(--radius-undesparche)] px-4 py-2 font-medium text-sm text-white shadow-xs focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] disabled:opacity-50 ${
                  danger
                    ? 'bg-[var(--color-undesparche-danger)] hover:bg-red-700'
                    : 'bg-[var(--color-undesparche-primary)] hover:bg-[var(--color-undesparche-primary-dark)]'
                }`}
              >
                {pending && (
                  <span className="material-symbols-outlined animate-spin text-sm" aria-hidden="true">
                    progress_activity
                  </span>
                )}
                {confirmLabel}
              </motion.button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body
  );
}
