import { AlertIcon, FilmIcon } from "./Icons";

export function Spinner({ className = "size-5" }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
    />
  );
}

export function SkeletonCard() {
  return (
    <div className="animate-pulse">
      <div className="aspect-video rounded-lg bg-zinc-800" />
      <div className="mt-3 h-4 w-3/4 rounded bg-zinc-800" />
      <div className="mt-2 h-3 w-1/2 rounded bg-zinc-800/70" />
    </div>
  );
}

export function EmptyState({ title, message }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-zinc-800 py-16 text-center">
      <FilmIcon className="size-10 text-zinc-600" />
      <p className="mt-4 font-medium text-zinc-300">{title}</p>
      {message && <p className="mt-1 text-sm text-zinc-500">{message}</p>}
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-red-900/60 bg-red-950/20 py-16 text-center">
      <AlertIcon className="size-10 text-red-400" />
      <p className="mt-4 font-medium text-zinc-200">Something went wrong</p>
      <p className="mt-1 text-sm text-zinc-400">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-5 rounded-md bg-zinc-800 px-4 py-2 text-sm font-medium hover:bg-zinc-700 transition"
        >
          Try again
        </button>
      )}
    </div>
  );
}
