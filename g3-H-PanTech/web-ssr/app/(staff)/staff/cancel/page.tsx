import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { cancelOrder } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import { formatDateTime, money } from '@/lib/format';
import type { Order } from '@/lib/types';

export const metadata = { title: 'Cancelar pedidos' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function CancelOrdersPage({ searchParams }: Props) {
  requirePermission('orders:read_all');

  let orders: Order[] = [];
  let error: string | undefined;
  try {
    orders = await apiFetch<Order[]>('/orders?status=pendiente&limit=100');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar los pedidos.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Supervisión"
        title="Cancelar pedidos pendientes"
        description="Solo se cancelan pedidos que todavía no se pagaron. Un pedido pagado ya puede estar en cocina y requiere un flujo de reembolso fuera de este prototipo."
        actions={
          <Link className="button button-secondary" href="/staff">
            Volver al panel
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      {orders.length === 0 ? (
        <EmptyState
          title="No hay pedidos pendientes"
          description="Todos los pedidos del turno ya fueron pagados o cancelados."
        />
      ) : (
        <div className="table-wrap card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Tipo</th>
                <th>Total</th>
                <th>Creado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <code>{order.id.slice(0, 8)}</code>
                  </td>
                  <td>
                    {order.order_type === 'en_mesa'
                      ? `Mesa ${order.table_number ?? ''}`
                      : 'Para llevar'}
                  </td>
                  <td>{money(order.total)}</td>
                  <td>{formatDateTime(order.created_at)}</td>
                  <td>
                    <form action={cancelOrder}>
                      <input type="hidden" name="order_id" value={order.id} />
                      <input type="hidden" name="return_to" value="/staff/cancel" />
                      <button className="button" type="submit">
                        Cancelar pedido
                      </button>
                    </form>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
