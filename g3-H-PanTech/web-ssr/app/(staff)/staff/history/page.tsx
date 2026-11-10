import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { apiFetch, ApiError } from '@/lib/api';
import { requireStaff } from '@/lib/auth';
import { formatDateTime, money } from '@/lib/format';
import type { HistoryPage } from '@/lib/types';

export const metadata = { title: 'Historial general' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: {
    error?: string | string[];
    ok?: string | string[];
    from?: string | string[];
    to?: string | string[];
    status?: string | string[];
    page?: string | string[];
  };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function StaffHistoryPage({ searchParams }: Props) {
  requireStaff();
  const page = Math.max(1, Number.parseInt(value(searchParams?.page) ?? '1', 10) || 1);
  const query = new URLSearchParams({ page: String(page), page_size: '20' });
  for (const key of ['from', 'to', 'status'] as const) {
    const current = value(searchParams?.[key]);
    if (current) query.set(key, current);
  }

  let history: HistoryPage = { items: [], total: 0, page, page_size: 20 };
  let error: string | undefined;
  try {
    history = await apiFetch<HistoryPage>(`/history?${query.toString()}`);
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el historial.';
  }

  const pages = Math.max(1, Math.ceil(history.total / history.page_size));
  const pageLink = (target: number) => {
    const next = new URLSearchParams(query);
    next.set('page', String(target));
    return `/staff/history?${next.toString()}`;
  };

  return (
    <main>
      <PageHeader
        eyebrow="Supervisión"
        title="Historial general"
        description="Pedidos pagados, sus ítems y la línea de tiempo de estados. Los clientes solo ven su propio historial."
        actions={
          <Link className="button button-secondary" href="/staff">
            Volver al panel
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <form className="inline-form card" method="get">
        <label>
          Desde
          <input type="date" name="from" defaultValue={value(searchParams?.from) ?? ''} />
        </label>
        <label>
          Hasta
          <input type="date" name="to" defaultValue={value(searchParams?.to) ?? ''} />
        </label>
        <label>
          Estado
          <select name="status" defaultValue={value(searchParams?.status) ?? ''}>
            <option value="">Todos</option>
            <option value="pagado">Pagado</option>
            <option value="en_preparacion">En preparación</option>
            <option value="listo">Listo</option>
            <option value="entregado">Entregado</option>
          </select>
        </label>
        <button className="button button-secondary" type="submit">
          Filtrar
        </button>
      </form>

      {history.items.length === 0 ? (
        <EmptyState
          title="Sin resultados"
          description="No hay pedidos pagados en el rango seleccionado."
        />
      ) : (
        <>
          <div className="table-wrap card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Estado</th>
                  <th>Total</th>
                  <th>Tipo</th>
                  <th>Pagado</th>
                  <th>Listo</th>
                  <th>Detalle</th>
                </tr>
              </thead>
              <tbody>
                {history.items.map((order) => (
                  <tr key={order.order_id}>
                    <td>
                      <code>{order.order_id.slice(0, 8)}</code>
                    </td>
                    <td>
                      <StatusBadge status={order.status} />
                    </td>
                    <td>{money(order.total)}</td>
                    <td>
                      {order.order_type === 'en_mesa'
                        ? `Mesa ${order.table_number ?? ''}`
                        : order.order_type ?? '—'}
                    </td>
                    <td>{formatDateTime(order.paid_at)}</td>
                    <td>{formatDateTime(order.ready_at)}</td>
                    <td>
                      <details>
                        <summary>Ver</summary>
                        <ul className="summary-list">
                          {order.items.map((item) => (
                            <li key={item.product_id}>
                              <span>
                                {item.quantity}× {item.name}
                              </span>
                              <strong>{money(item.unit_price)}</strong>
                            </li>
                          ))}
                        </ul>
                        <ol className="timeline compact">
                          {order.timeline.map((entry) => (
                            <li key={`${entry.status}-${entry.at}`} className="done">
                              <span className="timeline-dot" aria-hidden="true" />
                              <span>{entry.status}</span>
                              <span className="muted">{formatDateTime(entry.at)}</span>
                            </li>
                          ))}
                        </ol>
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pagination">
            <span className="muted">
              {history.total} pedidos · página {history.page} de {pages}
            </span>
            {page > 1 ? <Link href={pageLink(page - 1)}>Anterior</Link> : null}
            {page < pages ? <Link href={pageLink(page + 1)}>Siguiente</Link> : null}
          </div>
        </>
      )}
    </main>
  );
}
