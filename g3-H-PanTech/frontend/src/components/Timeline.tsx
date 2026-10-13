import { useEffect, useState } from 'react'
import { api } from '../api'
import type { OrderStatus, StatusEvent } from '../types'

const LABELS: Record<string, string> = {
  pendiente: 'Pendiente',
  en_preparacion: 'En preparación',
  listo: 'Listo',
  entregado: 'Entregado',
}

function fmtTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

/** Línea de tiempo del historial de estados de una comanda (Requerimiento 9). */
export default function Timeline({
  orderId,
  status,
}: {
  orderId: number
  status: OrderStatus
}) {
  const [events, setEvents] = useState<StatusEvent[]>([])

  // Se recarga cuando cambia el estado (no en cada ciclo de polling).
  useEffect(() => {
    let active = true
    api
      .orderHistory(orderId)
      .then((h) => {
        if (active) setEvents(h.events)
      })
      .catch(() => {
        if (active) setEvents([])
      })
    return () => {
      active = false
    }
  }, [orderId, status])

  if (events.length === 0) return null
  const lastIndex = events.length - 1

  return (
    <div>
      <div className="hist-title">Historial</div>
      <ul className="timeline">
        {events.map((event, index) => (
          <li key={`${event.status}-${index}`} className={index === lastIndex ? 'current' : 'done'}>
            <span className="t">{fmtTime(event.changed_at)}</span>
            <strong>{LABELS[event.status] ?? event.status}</strong>
          </li>
        ))}
      </ul>
    </div>
  )
}
