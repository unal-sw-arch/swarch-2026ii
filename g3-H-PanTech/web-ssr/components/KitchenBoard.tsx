'use client';

import { useEffect, useState } from 'react';
import { formatDateTime, statusLabel } from '@/lib/format';
import type { QueueResponse } from '@/lib/types';

export default function KitchenBoard({ initial }: { initial: QueueResponse }) {
  const [queue, setQueue] = useState(initial);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setInterval(async () => {
      try {
        const response = await fetch('/api/queue', { cache: 'no-store' });
        if (!response.ok) return;
        const body = (await response.json()) as QueueResponse;
        if (!cancelled) {
          setQueue(body);
          setError(null);
        }
      } catch {
        if (!cancelled) setError('No se pudo actualizar la cola.');
      }
    }, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return (
    <>
      <div className="queue-summary">
        <span className="badge badge-accent">
          {queue.items.length} comanda{queue.items.length === 1 ? '' : 's'} activa
          {queue.items.length === 1 ? '' : 's'}
        </span>
        <span className="badge">
          En preparación: {queue.capacity.in_preparation}/{queue.capacity.max}
        </span>
        <span className="muted">Actualizado: {formatDateTime(queue.generated_at)}</span>
      </div>
      {error ? <p className="form-error">{error}</p> : null}
      {queue.items.length === 0 ? (
        <p className="muted">No hay comandas activas en este momento.</p>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Pedido</th>
                <th>Tipo</th>
                <th>Estado</th>
                <th>Preparación</th>
                <th>Espera</th>
                <th>ETA</th>
                <th>Ítems</th>
              </tr>
            </thead>
            <tbody>
              {queue.items.map((item) => (
                <tr key={item.order_id}>
                  <td>{item.position}</td>
                  <td>
                    <code>{item.order_id.slice(0, 8)}</code>
                  </td>
                  <td>{item.order_type === 'en_mesa' ? 'En mesa' : 'Para llevar'}</td>
                  <td>{statusLabel(item.status)}</td>
                  <td>{item.prep_minutes} min</td>
                  <td>{item.wait_minutes} min</td>
                  <td>{item.eta_minutes} min</td>
                  <td>
                    {item.items
                      .map((line) => `${line.quantity}× ${line.name}`)
                      .join(', ') || '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
