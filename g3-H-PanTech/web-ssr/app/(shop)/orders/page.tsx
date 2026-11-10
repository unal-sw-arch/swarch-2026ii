import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { formatDateTime, money } from '@/lib/format';
import type { Order } from '@/lib/types';

export const metadata = { title: 'Mis pedidos' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function OrdersPage({ searchParams }: Props) {
  requireSession();
  let orders: Order[] = [];
  let error: string | undefined;
  try {
    orders = await apiFetch<Order[]>('/orders?limit=100');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar los pedidos.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Historial propio"
        title="Mis pedidos"
        description="Solo ves los pedidos asociados a tu cuenta. El personal autorizado puede ver el historial general desde el backoffice."
        actions={
          <Link className="button" href="/menu">
            Hacer un pedido
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      {!error && orders.length === 0 ? (
        <EmptyState
          title="Todavía no tienes pedidos"
          description="Cuando pagues tu primer pedido aparecerá aquí con su estado en tiempo real."
          action={<Link className="button" href="/menu">Ver el menú</Link>}
        />
      ) : null}

      {orders.length > 0 ? (
        <div className="order-list">
          {orders.map((order) => (
            <article className="order-card" key={order.id}>
              <div className="order-card-head">
                <div>
                  <p className="eyebrow">Pedido</p>
                  <h2>
                    <Link href={`/orders/${order.id}`}>
                      <code>{order.id.slice(0, 8)}</code>
                    </Link>
                  </h2>
                  <p className="muted">
                    {order.order_type === 'en_mesa'
                      ? `En mesa ${order.table_number ?? ''}`
                      : 'Para llevar'}{' '}
                    · {formatDateTime(order.created_at)}
                  </p>
                </div>
                <StatusBadge status={order.status} />
              </div>
              <p className="order-total">{money(order.total)}</p>
              <p className="muted">
                {order.items.map((item) => `${item.quantity}× ${item.product_name}`).join(' · ')}
              </p>
              <div className="order-actions">
                <Link className="button button-secondary" href={`/orders/${order.id}`}>
                  Ver seguimiento
                </Link>
                {order.status === 'pendiente' ? (
                  <Link className="button" href={`/checkout/payment?orderId=${order.id}`}>
                    Pagar
                  </Link>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </main>
  );
}
