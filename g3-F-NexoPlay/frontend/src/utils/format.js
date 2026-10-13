// 754.2 seconds -> "12:34"; hours only when needed
export function formatDuration(seconds) {
  if (seconds == null) return null;
  const total = Math.round(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = String(total % 60).padStart(2, "0");
  return h ? `${h}:${String(m).padStart(2, "0")}:${s}` : `${m}:${s}`;
}

export const statusColors = {
  PENDING: "text-zinc-400",
  PROCESSING: "text-rose-500 animate-pulse",
  COMPLETED: "text-zinc-100 font-bold",
  FAILED: "text-red-600",
};
