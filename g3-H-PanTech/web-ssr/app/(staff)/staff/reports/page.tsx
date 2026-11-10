import Link from 'next/link';
import Alert from '@/components/Alert';
import ExportReportForm from '@/components/ExportReportForm';
import PageHeader from '@/components/PageHeader';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import { daysAgoISO, formatDate, money, todayISO } from '@/lib/format';
import type { SalesReport, TimesReport } from '@/lib/types';

export const metadata = { title: 'Reportes' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: {
    error?: string | string[];
    ok?: string | string[];
    from?: string | string[];
    to?: string | string[];
  };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function ReportsPage({ searchParams }: Props) {
  requirePermission('reports:read');
  const from = value(searchParams?.from) ?? daysAgoISO(29);
  const to = value(searchParams?.to) ?? todayISO();
  const query = new URLSearchParams({ from, to }).toString();

  let sales: SalesReport | null = null;
  let times: TimesReport | null = null;
  let error: string | undefined;
  try {
    [sales, times] = await Promise.all([
      apiFetch<SalesReport>(`/reports/sales?${query}`),
      apiFetch<TimesReport>(`/reports/times?${query}`),
    ]);
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar los reportes.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Supervisión"
        title="Reportes del negocio"
        description="Ventas, ticket promedio, productos más vendidos y tiempos de preparación/entrega calculados por Reporting Service."
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
          <input type="date" name="from" defaultValue={from} />
        </label>
        <label>
          Hasta
          <input type="date" name="to" defaultValue={to} />
        </label>
        <button className="button button-secondary" type="submit">
          Actualizar
        </button>
      </form>

      {sales ? (
        <>
          <section className="metric-grid">
            <article className="metric-card">
              <span className="muted">Pedidos pagados</span>
              <strong>{sales.orders_count}</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Ingresos</span>
              <strong>{money(sales.revenue)}</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Ticket promedio</span>
              <strong>{money(sales.average_ticket)}</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Rango</span>
              <strong>
                {formatDate(sales.from)} — {formatDate(sales.to)}
              </strong>
            </article>
          </section>

          <div className="two-column">
            <section className="card">
              <h2>Ventas por día</h2>
              {sales.by_day.length === 0 ? (
                <p className="muted">Sin ventas en el rango.</p>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Día</th>
                        <th>Pedidos</th>
                        <th>Ingresos</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sales.by_day.map((day) => (
                        <tr key={day.day}>
                          <td>{formatDate(day.day)}</td>
                          <td>{day.orders}</td>
                          <td>{money(day.revenue)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="card">
              <h2>Productos más vendidos</h2>
              {sales.top_products.length === 0 ? (
                <p className="muted">Sin productos vendidos en el rango.</p>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Producto</th>
                        <th>Cantidad</th>
                        <th>Ingresos</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sales.top_products.map((product) => (
                        <tr key={product.product_id}>
                          <td>{product.name}</td>
                          <td>{product.quantity}</td>
                          <td>{money(product.revenue)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </div>
        </>
      ) : null}

      {times ? (
        <section className="card" style={{ marginTop: '1.5rem' }}>
          <h2>Tiempos del servicio</h2>
          <div className="metric-grid">
            <article className="metric-card">
              <span className="muted">Pedidos medidos</span>
              <strong>{times.orders_measured}</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Preparación promedio</span>
              <strong>{times.avg_prep_minutes ?? '—'} min</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Entrega promedio</span>
              <strong>{times.avg_delivery_minutes ?? '—'} min</strong>
            </article>
            <article className="metric-card">
              <span className="muted">Total promedio</span>
              <strong>{times.avg_total_minutes ?? '—'} min</strong>
            </article>
          </div>
        </section>
      ) : null}

      <section className="card" style={{ marginTop: '1.5rem' }}>
        <h2>Exportar</h2>
        <p className="muted">
          Reporting Service genera el archivo en RustFS y devuelve una URL firmada temporal.
        </p>
        <ExportReportForm defaultFrom={from} defaultTo={to} />
      </section>
    </main>
  );
}
