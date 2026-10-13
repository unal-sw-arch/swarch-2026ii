import React, { createContext, useContext, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  title: string;
  description?: string;
  duration?: number;
}

interface ToastContextValue {
  showToast: (toast: Omit<ToastItem, 'id'>) => string;
  success: (title: string, description?: string) => string;
  error: (title: string, description?: string) => string;
  info: (title: string, description?: string) => string;
  dismissToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    ({ type, title, description, duration = 4000 }: Omit<ToastItem, 'id'>) => {
      const id = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      const newToast: ToastItem = { id, type, title, description, duration };

      setToasts((prev) => [...prev, newToast]);

      if (duration > 0) {
        setTimeout(() => {
          dismissToast(id);
        }, duration);
      }

      return id;
    },
    [dismissToast]
  );

  const success = useCallback(
    (title: string, description?: string) => showToast({ type: 'success', title, description }),
    [showToast]
  );

  const error = useCallback(
    (title: string, description?: string) => showToast({ type: 'error', title, description }),
    [showToast]
  );

  const info = useCallback(
    (title: string, description?: string) => showToast({ type: 'info', title, description }),
    [showToast]
  );

  return (
    <ToastContext.Provider value={{ showToast, success, error, info, dismissToast }}>
      {children}
      {/* Toast viewport container */}
      <div
        aria-live="polite"
        aria-atomic="true"
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col items-end gap-2.5 sm:bottom-6 sm:right-6 max-w-sm w-full px-4 sm:px-0"
      >
        <AnimatePresence mode="popLayout">
          {toasts.map((toast) => (
            <motion.div
              key={toast.id}
              layout
              initial={{ opacity: 0, y: 16, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.95, transition: { duration: 0.15 } }}
              transition={{ type: 'spring', duration: 0.35, bounce: 0 }}
              className="pointer-events-auto w-full heroui-card p-4 shadow-xl border backdrop-blur-xl flex items-start gap-3 bg-white/95 dark:bg-[#0E1720]/95"
            >
              {/* Type Icon */}
              <span className="mt-0.5 shrink-0 flex items-center justify-center">
                {toast.type === 'success' && (
                  <span className="material-symbols-outlined text-xl text-[#00875A] dark:text-[#00F5A0]">
                    check_circle
                  </span>
                )}
                {toast.type === 'error' && (
                  <span className="material-symbols-outlined text-xl text-red-500">
                    error
                  </span>
                )}
                {toast.type === 'info' && (
                  <span className="material-symbols-outlined text-xl text-blue-500">
                    info
                  </span>
                )}
              </span>

              {/* Text content */}
              <div className="flex-1 min-w-0 pr-1">
                <p className="font-display font-bold text-sm text-neutral-900 dark:text-white leading-tight">
                  {toast.title}
                </p>
                {toast.description && (
                  <p className="mt-1 text-xs text-neutral-600 dark:text-neutral-300 leading-relaxed">
                    {toast.description}
                  </p>
                )}
              </div>

              {/* Dismiss button */}
              <button
                type="button"
                onClick={() => dismissToast(toast.id)}
                aria-label="Cerrar notificación"
                className="shrink-0 rounded-lg p-1 text-neutral-400 hover:text-neutral-700 dark:hover:text-white hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
              >
                <span className="material-symbols-outlined text-base">close</span>
              </button>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
