import Link from 'next/link';
import { notFound } from 'next/navigation';
import Alert from '@/components/Alert';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { payOrder } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { money } from '@/lib/format';
import type { Order } from '@/lib/types';

export const metadata = { title: 'Pago simulado' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: {
    orderId?: string | string[];
    error?: string | string[];
    ok?: string | string[];
  };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function PaymentPage({ searchParams }: Props) {
  requireSession();
  const orderId = value(searchParams?.orderId);
  if (!orderId) notFound();

  let order: Order | null = null;
  let error: string | undefined;
  try {
    order = await apiFetch<Order>(`/orders/${orderId}`);
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) notFound();
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el pedido.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Pago"
        title="Confirma el pago simulado"
        description="El total mostrado viene del servidor. Order Service rechazará cualquier monto distinto al total calculado."
        actions={
          <Link className="button button-secondary" href="/orders">
            Mis pedidos
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      {order ? (
        <div className="payment-card card">
          <div className="payment-head">
            <div>
              <p className="eyebrow">Pedido</p>
              <h2>
                <code>{order.id}</code>
              </h2>
            </div>
            <StatusBadge status={order.status} />
          </div>

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

          <p className="total-line">
            Total a pagar <strong>{money(order.total)}</strong>
          </p>

          {order.status === 'pendiente' ? (
            <form action={payOrder} className="form">
              <input type="hidden" name="order_id" value={order.id} />
              <input type="hidden" name="return_to" value={`/orders/${order.id}`} />
              <button className="button" type="submit">
                Pagar {money(order.total)}
              </button>
              <p className="muted">
                Pago simulado: no se cobra dinero real. Se usa una llave de idempotencia para que
                reintentar no duplique el pago.
              </p>
            </form>
          ) : (
            <p className="notice">
              Este pedido ya está <StatusBadge status={order.status} />. Puedes seguirlo desde{' '}
              <Link href={`/orders/${order.id}`}>su detalle</Link>.
            </p>
          )}
        </div>
      ) : null}
    </main>
  );
}
