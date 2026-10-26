import { useState } from 'react'
import { api, POLL_INTERVAL_MS } from '../api'
import { usePolling } from '../hooks'
import Timeline from '../components/Timeline'
import type { Dispatch, QueueOrder } from '../types'

function DispatchColumn({
  title,
  orders,
  onDeliver,
}: {
  title: string
  orders: QueueOrder[]
  onDeliver: (id: number) => void
}) {
  return (
    <div className="dispatch-column">
      <h3>
        {title} <span className="badge">{orders.length}</span>
      </h3>
      {orders.length === 0 && <p className="muted">Sin pedidos listos.</p>}
      {orders.map((order) => (
        <article key={order.order_id} className="dispatch-card">
          <header>
            <strong>
              Pedido #{order.order_id}
              {order.type === 'en_mesa' ? ` · Mesa ${order.table_number}` : ''}
            </strong>
            <span className="muted">⏱ {order.estimated_minutes} min</span>
          </header>
          <ul>
            {order.items.map((item) => (
              <li key={item.product_id}>
                {item.quantity}× {item.name}
              </li>
            ))}
          </ul>
          <button className="success" onClick={() => onDeliver(order.order_id)}>
            Entregar
          </button>
          <Timeline orderId={order.order_id} status={order.status} />
        </article>
      ))}
    </div>
  )
}

export default function DespachadorView() {
  const { data, error, refresh } = usePolling<Dispatch>(api.dispatch, POLL_INTERVAL_MS)
  const [actionError, setActionError] = useState<string | null>(null)
  const dispatch: Dispatch = data ?? { para_llevar: [], en_mesa: [] }

  const deliver = async (orderId: number) => {
    try {
      await api.deliver(orderId)
      await refresh()
      setActionError(null)
    } catch (err) {
      setActionError((err as Error).message)
    }
  }

  return (
    <section>
      <div className="section-header">
        <h2>Despacho</h2>
        <span className="muted">
          Pedidos listos · actualiza cada {Math.round(POLL_INTERVAL_MS / 1000)} s
        </span>
      </div>
      {(error || actionError) && <p className="error">{error || actionError}</p>}
      <div className="dispatch-grid">
        <DispatchColumn title="Para llevar" orders={dispatch.para_llevar} onDeliver={deliver} />
        <DispatchColumn title="En mesa" orders={dispatch.en_mesa} onDeliver={deliver} />
      </div>
    </section>
  )
}
