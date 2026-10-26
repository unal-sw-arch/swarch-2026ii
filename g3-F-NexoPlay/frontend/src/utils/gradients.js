// Placeholder backgrounds for videos without a thumbnail
const gradients = [
  "from-rose-700 via-red-800 to-zinc-900",
  "from-indigo-700 via-violet-800 to-zinc-900",
  "from-emerald-700 via-teal-800 to-zinc-900",
  "from-amber-600 via-orange-800 to-zinc-900",
  "from-sky-700 via-blue-800 to-zinc-900",
  "from-fuchsia-700 via-pink-800 to-zinc-900",
];

export function gradientFor(id) {
  const n = [...String(id)].reduce((sum, c) => sum + c.charCodeAt(0), 0);
  return gradients[n % gradients.length];
}
