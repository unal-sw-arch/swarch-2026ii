import React from 'react';
import { Spinner } from '@heroui/react';

export interface LoadingSpinnerProps {
  label?: string;
  size?: 'sm' | 'md' | 'lg';
  fullScreen?: boolean;
}

export function LoadingSpinner({
  label = 'Cargando…',
  size = 'md',
  fullScreen = false,
}: LoadingSpinnerProps): React.JSX.Element {
  const content = (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center gap-3 p-6"
    >
      <Spinner size={size} className="text-[var(--color-undesparche-primary)]" />
      <span className="font-medium text-sm text-gray-700 dark:text-gray-300">
        {label}
      </span>
      <span className="sr-only">{label}</span>
    </div>
  );

  if (fullScreen) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-white/80 backdrop-blur-sm dark:bg-black/80">
        {content}
      </div>
    );
  }

  return content;
}
