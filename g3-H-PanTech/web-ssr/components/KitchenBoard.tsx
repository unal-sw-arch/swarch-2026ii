'use client';

import { useCallback, useEffect, useState } from 'react';
import { formatDateTime, statusLabel } from '@/lib/format';
import type { QueueResponse } from '@/lib/types';

export default function KitchenBoard({
  initial,
  dispatchOnly = false,
  canUpdate = false,
  canDeliver = false,
}: {
  initial: QueueResponse;
  dispatchOnly?: boolean;
  canUpdate?: boolean;
  canDeliver?: boolean;
}) {
  const [queue, setQueue] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyOrder, setBusyOrder] = useState<string | null>(null);

  const refreshQueue = useCallback(async () => {
    const response = await fetch('/api/queue', { cache: 'no-store' });
    const body = (await response.json()) as QueueResponse | { message?: string };
    if (!response.ok) {
      throw new Error(
        ('message' in body && body.message) || 'No se pudo actualizar la cola.',
      );
    }
    if (!('items' in body)) {
      throw new Error('La respuesta de la cola tiene un formato inválido.');
    }
    setQueue(body);
    setError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setInterval(async () => {
      try {
        await refreshQueue();
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'No se pudo actualizar la cola.');
        }
      }
    }, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [refreshQueue]);

  const performAction = async (
    orderId: string,
    action: 'status' | 'deliver',
    status?: 'en_preparacion' | 'listo',
  ) => {
    setBusyOrder(orderId);
    setError(null);
    setNotice(null);
    try {
      const response = await fetch(`/api/queue/orders/${encodeURIComponent(orderId)}/${action}`, {
        method: action === 'status' ? 'PATCH' : 'POST',
        headers: action === 'status' ? { 'content-type': 'application/json' } : undefined,
        body: status ? JSON.stringify({ status }) : undefined,
      });
      const body = (await response.json()) as { message?: string };
      if (!response.ok) {
        throw new Error(body.message ?? 'No se pudo completar la acción.');
      }
      await refreshQueue();
      setNotice(
        action === 'deliver'
          ? 'Entrega confirmada.'
          : status === 'listo'
            ? 'Pedido marcado como listo.'
            : 'Preparación iniciada.',
      );
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'No se pudo completar la acción.');
    } finally {
      setBusyOrder(null);
    }
  };

  return (
    <>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {notice ? <p className="form-success" role="status">{notice}</p> : null}
      <div className="queue-summary">
        <span className="badge badge-accent">
          {dispatchOnly
            ? `${queue.items.length} pedido${queue.items.length === 1 ? '' : 's'} listo${queue.items.length === 1 ? '' : 's'}`
            : `${queue.items.length} comanda${queue.items.length === 1 ? '' : 's'} activa${queue.items.length === 1 ? '' : 's'}`}
        </span>
        {!dispatchOnly ? (
          <span className="badge">
            En preparación: {queue.capacity.in_preparation}/{queue.capacity.max}
          </span>
        ) : null}
        <span className="muted">Actualizado: {formatDateTime(queue.generated_at)}</span>
      </div>
      {queue.items.length === 0 ? (
        <p className="muted">
          {dispatchOnly
            ? 'No hay pedidos listos para entregar en este momento.'
            : 'No hay comandas activas en este momento.'}
        </p>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                {!dispatchOnly ? <th>#</th> : null}
                <th>Pedido</th>
                <th>Tipo</th>
                <th>Estado</th>
                {!dispatchOnly ? <th>Preparación</th> : null}
                {!dispatchOnly ? <th>Espera</th> : null}
                {!dispatchOnly ? <th>ETA</th> : null}
                <th>Ítems</th>
                {canUpdate || canDeliver ? <th>Acciones</th> : null}
              </tr>
            </thead>
            <tbody>
              {queue.items.map((item) => (
                <tr key={item.order_id}>
                  {!dispatchOnly ? <td>{item.position}</td> : null}
                  <td>
                    <code>{item.order_id.slice(0, 8)}</code>
                  </td>
                  <td>{item.order_type === 'en_mesa' ? 'En mesa' : 'Para llevar'}</td>
                  <td>{statusLabel(item.status)}</td>
                  {!dispatchOnly ? <td>{item.prep_minutes} min</td> : null}
                  {!dispatchOnly ? <td>{item.wait_minutes} min</td> : null}
                  {!dispatchOnly ? <td>{item.eta_minutes} min</td> : null}
                  <td>
                    {item.items
                      .map((line) => `${line.quantity}× ${line.name}`)
                      .join(', ') || '—'}
                  </td>
                  {canUpdate || canDeliver ? (
                    <td>
                      {canUpdate && item.status === 'pendiente' ? (
                        <button
                          className="button"
                          disabled={busyOrder === item.order_id}
                          onClick={() =>
                            performAction(item.order_id, 'status', 'en_preparacion')
                          }
                        >
                          Empezar
                        </button>
                      ) : null}
                      {canUpdate && item.status === 'en_preparacion' ? (
                        <button
                          className="button"
                          disabled={busyOrder === item.order_id}
                          onClick={() => performAction(item.order_id, 'status', 'listo')}
                        >
                          Marcar listo
                        </button>
                      ) : null}
                      {canDeliver && item.status === 'listo' ? (
                        <button
                          className="button"
                          disabled={busyOrder === item.order_id}
                          onClick={() => performAction(item.order_id, 'deliver')}
                        >
                          Confirmar entrega
                        </button>
                      ) : null}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
