import Link from 'next/link';
import { notFound } from 'next/navigation';
import Alert from '@/components/Alert';
import OrderStatusPoller from '@/components/OrderStatusPoller';
import PageHeader from '@/components/PageHeader';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { formatDateTime, money, statusLabel } from '@/lib/format';
import type { Order } from '@/lib/types';

export const metadata = { title: 'Seguimiento del pedido' };
export const dynamic = 'force-dynamic';

const FLOW = ['pendiente', 'pagado', 'en_preparacion', 'listo', 'entregado'];

type Props = {
  params: { id: string };
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function OrderDetailPage({ params, searchParams }: Props) {
  requireSession();
  let order: Order | null = null;
  let error: string | undefined;
  try {
    order = await apiFetch<Order>(`/orders/${params.id}`);
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) notFound();
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el pedido.';
  }

  if (!order) {
    return (
      <main>
        <PageHeader title="Pedido no disponible" />
        <Alert error={error} />
      </main>
    );
  }

  const currentIndex = FLOW.indexOf(order.status);
  const canceled = order.status === 'cancelado';

  return (
    <main>
      <PageHeader
        eyebrow="Seguimiento"
        title={`Pedido ${order.id.slice(0, 8)}`}
        description={`Creado el ${formatDateTime(order.created_at)}. El estado se actualiza automáticamente.`}
        actions={
          <Link className="button button-secondary" href="/orders">
            Volver a mis pedidos
          </Link>
        }
      />
      <Alert error={value(searchParams?.error)} ok={value(searchParams?.ok)} />

      <div className="tracking-layout">
        <section className="card">
          <h2>Estado</h2>
          <OrderStatusPoller orderId={order.id} initialStatus={order.status} />
          {canceled ? (
            <p className="form-error">Este pedido fue cancelado.</p>
          ) : (
            <ol className="timeline">
              {FLOW.map((step, index) => {
                const reached = index <= currentIndex;
                const current = index === currentIndex;
                return (
                  <li key={step} className={reached ? 'done' : ''}>
                    <span className="timeline-dot" aria-hidden="true" />
                    <span>{statusLabel(step)}</span>
                    {current ? <span className="badge badge-accent">Actual</span> : null}
                  </li>
                );
              })}
            </ol>
          )}
          {order.status === 'pendiente' ? (
            <Link className="button" href={`/checkout/payment?orderId=${order.id}`}>
              Pagar ahora
            </Link>
          ) : null}
        </section>

        <section className="card">
          <h2>Detalle</h2>
          <dl className="detail-list">
            <div>
              <dt>Tipo</dt>
              <dd>
                {order.order_type === 'en_mesa'
                  ? `En mesa ${order.table_number ?? ''}`
                  : 'Para llevar'}
              </dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd className="total-amount">{money(order.total)}</dd>
            </div>
            <div>
              <dt>Notas</dt>
              <dd>{order.notes || 'Sin notas'}</dd>
            </div>
          </dl>
          <ul className="summary-list">
            {order.items.map((item) => (
              <li key={item.product_id}>
                <span>
                  {item.quantity}× {item.product_name}
                </span>
                <strong>{money(item.line_total)}</strong>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  );
}
