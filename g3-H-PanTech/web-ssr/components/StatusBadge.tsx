import { statusLabel } from '@/lib/format';

const TONE: Record<string, string> = {
  pendiente: 'badge-warn',
  pagado: 'badge-accent',
  en_preparacion: 'badge-accent',
  listo: 'badge-ok',
  entregado: 'badge-ok',
  cancelado: 'badge-danger',
};

export default function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${TONE[status] ?? ''}`}>{statusLabel(status)}</span>;
}
