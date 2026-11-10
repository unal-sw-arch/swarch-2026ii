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

// ISO date -> "just now", "5 min ago", "3 h ago", "2 d ago", then a date
export function formatRelativeTime(iso, now = Date.now()) {
  const date = new Date(iso);
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000));
  if (Number.isNaN(seconds)) return "";
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  if (seconds < 7 * 86400) return `${Math.floor(seconds / 86400)} d ago`;
  return date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

// Compact counts: 999 -> "999", 1200 -> "1.2K", 3400000 -> "3.4M"
export function formatCount(n) {
  return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(n);
}
