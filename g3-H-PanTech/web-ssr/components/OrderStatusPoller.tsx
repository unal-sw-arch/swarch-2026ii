'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { statusLabel } from '@/lib/format';
import type { OrderStatus, OrderStatusResponse } from '@/lib/types';

const TONE: Record<string, string> = {
  pendiente: 'badge-warn',
  pagado: 'badge-accent',
  en_preparacion: 'badge-accent',
  listo: 'badge-ok',
  entregado: 'badge-ok',
  cancelado: 'badge-danger',
};

export default function OrderStatusPoller({
  orderId,
  initialStatus,
}: {
  orderId: string;
  initialStatus: OrderStatus;
}) {
  const router = useRouter();
  const [status, setStatus] = useState<OrderStatus>(initialStatus);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let last = initialStatus;

    async function poll() {
      try {
        const response = await fetch(`/api/orders/${orderId}/status`, { cache: 'no-store' });
        if (!response.ok) return;
        const body = (await response.json()) as OrderStatusResponse;
        if (cancelled) return;
        setError(null);
        setStatus(body.status);
        setUpdatedAt(body.updated_at);
        if (body.status !== last) {
          last = body.status;
          router.refresh();
        }
      } catch {
        if (!cancelled) setError('No se pudo consultar el estado. Reintentando…');
      }
    }

    const timer = window.setInterval(poll, 4000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [initialStatus, orderId, router]);

  return (
    <div className="status-poller" aria-live="polite">
      <span className={`badge ${TONE[status] ?? ''}`}>{statusLabel(status)}</span>
      <span className="muted">Actualizado automáticamente cada 4 segundos</span>
      {updatedAt ? <span className="muted">Último cambio: {updatedAt}</span> : null}
      {error ? <span className="form-error">{error}</span> : null}
    </div>
  );
}
