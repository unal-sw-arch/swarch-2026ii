import React from 'react';

export interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

export function EmptyState({
  icon = 'inbox',
  title,
  description,
  actionLabel,
  onAction,
  className = '',
}: EmptyStateProps): React.JSX.Element {
  return (
    <div
      className={`heroui-card border-dashed border-neutral-300 bg-neutral-50/50 p-8 text-center dark:border-neutral-800 dark:bg-[#0E1720]/40 flex flex-col items-center justify-center ${className}`}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
        <span className="material-symbols-outlined text-2xl" aria-hidden="true">
          {icon}
        </span>
      </div>
      <h3 className="mt-4 font-display font-bold text-base text-neutral-900 dark:text-neutral-100">
        {title}
      </h3>
      {description && (
        <p className="mt-1 max-w-sm text-xs text-neutral-500 dark:text-neutral-400">
          {description}
        </p>
      )}
      {actionLabel && onAction && (
        <button
          type="button"
          onClick={onAction}
          className="heroui-btn-solid mt-4 inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}
