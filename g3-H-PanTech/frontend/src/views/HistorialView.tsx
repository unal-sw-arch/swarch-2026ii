import { api, POLL_INTERVAL_MS } from '../api'
import { usePolling } from '../hooks'
import type { HistoryRow } from '../types'

const LABELS: Record<string, string> = {
  pendiente: 'Pendiente',
  en_preparacion: 'En preparación',
  listo: 'Listo',
  entregado: 'Entregado',
}

function fmtDateTime(iso: string | null) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

function fmtDuration(seconds: number | null, delivered: boolean) {
  if (seconds == null) return '—'
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  const base = `${minutes}m ${String(rest).padStart(2, '0')}s`
  return delivered ? base : `${base} y contando`
}

export default function HistorialView() {
  const { data, error } = usePolling<HistoryRow[]>(api.historyList, POLL_INTERVAL_MS)
  const rows: HistoryRow[] = data ?? []

  return (
    <section>
      <div className="section-header">
        <h2>Historial de comandas</h2>
        <span className="muted">
          Registro durable en PostgreSQL · actualiza cada {Math.round(POLL_INTERVAL_MS / 1000)} s
        </span>
      </div>
      {error && <p className="error">{error}</p>}
      {rows.length === 0 && <p className="muted">Sin comandas registradas.</p>}

      {rows.length > 0 && (
        <table className="menu-table">
          <thead>
            <tr>
              <th>Pedido</th>
              <th>Inicio</th>
              <th>Listo</th>
              <th>Entregado</th>
              <th>Duración</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.order_id}>
                <td>
                  <strong>#{row.order_id}</strong>
                </td>
                <td>{fmtDateTime(row.started_at)}</td>
                <td>{fmtDateTime(row.ready_at)}</td>
                <td>{fmtDateTime(row.delivered_at)}</td>
                <td>{fmtDuration(row.duration_seconds, row.status === 'entregado')}</td>
                <td>
                  <span className={`tag ${row.status}`}>
                    {LABELS[row.status] ?? row.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
