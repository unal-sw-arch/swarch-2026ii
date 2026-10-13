import { useState } from 'react'
import { api, POLL_INTERVAL_MS } from '../api'
import { usePolling } from '../hooks'
import Timeline from '../components/Timeline'
import type { QueueOrder } from '../types'

export default function CocineroView() {
  const { data, error, refresh } = usePolling<QueueOrder[]>(api.queue, POLL_INTERVAL_MS)
  const orders: QueueOrder[] = data ?? []
  const [actionError, setActionError] = useState<string | null>(null)

  const changeStatus = async (orderId: number, status: 'en_preparacion' | 'listo') => {
    try {
      await api.setStatus(orderId, status)
      await refresh()
      setActionError(null)
    } catch (err) {
      setActionError((err as Error).message)
    }
  }

  return (
    <section>
      <div className="section-header">
        <h2>Cola de cocina</h2>
        <span className="muted">
          Actualiza cada {Math.round(POLL_INTERVAL_MS / 1000)} s · prioridad por tiempo estimado
        </span>
      </div>
      {(error || actionError) && <p className="error">{error || actionError}</p>}
      {orders.length === 0 && <p className="muted">No hay comandas pendientes.</p>}

      <div className="cards">
        {orders.map((order, index) => (
          <article key={order.order_id} className={`kitchen-card ${order.status}`}>
            <header>
              <span className="priority">#{index + 1}</span>
              <strong>
                Pedido #{order.order_id} ·{' '}
                {order.type === 'en_mesa' ? `Mesa ${order.table_number}` : 'Para llevar'}
              </strong>
              <span className={`tag ${order.status}`}>
                {order.status === 'en_preparacion' ? 'En preparación' : 'Pendiente'}
              </span>
            </header>
            <div className="estimate">⏱ {order.estimated_minutes} min estimados</div>
            <ul>
              {order.items.map((item) => (
                <li key={item.product_id}>
                  {item.quantity}× {item.name}
                </li>
              ))}
            </ul>
            <div className="actions">
              {order.status === 'pendiente' && (
                <button
                  className="primary"
                  onClick={() => changeStatus(order.order_id, 'en_preparacion')}
                >
                  Empezar
                </button>
              )}
              {order.status === 'en_preparacion' && (
                <button className="success" onClick={() => changeStatus(order.order_id, 'listo')}>
                  Listo
                </button>
              )}
            </div>
            <Timeline orderId={order.order_id} status={order.status} />
          </article>
        ))}
      </div>
    </section>
  )
}
