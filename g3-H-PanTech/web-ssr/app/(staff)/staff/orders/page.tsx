import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { createStaffOrder, payOrder } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireStaff } from '@/lib/auth';
import { money } from '@/lib/format';
import { can } from '@/lib/permissions';
import type { MenuItem, Order } from '@/lib/types';

export const metadata = { title: 'Pedidos de mostrador' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: {
    error?: string | string[];
    ok?: string | string[];
    status?: string | string[];
  };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function StaffOrdersPage({ searchParams }: Props) {
  const session = requireStaff();
  const status = value(searchParams?.status);
  const query = new URLSearchParams({ limit: '100' });
  if (status) query.set('status', status);

  let menu: MenuItem[] = [];
  let orders: Order[] = [];
  let error: string | undefined;
  try {
    [menu, orders] = await Promise.all([
      apiFetch<MenuItem[]>('/menu'),
      apiFetch<Order[]>(`/orders?${query.toString()}`),
    ]);
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar los pedidos.';
  }

  const canCharge = can(session, 'orders:charge');

  return (
    <main>
      <PageHeader
        eyebrow="Mesero"
        title="Pedidos de mostrador"
        description="Crea pedidos para llevar o en mesa, cobra con pago simulado y consulta el estado de cada orden."
        actions={
          <>
            <Link className="button button-secondary" href="/staff/reservations">
              Reservas
            </Link>
            <Link className="button button-secondary" href="/staff">
              Volver al panel
            </Link>
          </>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <div className="two-column two-column-wide">
        <form action={createStaffOrder} className="card form">
          <h2>Nuevo pedido</h2>
          <div className="form-row">
            <div className="field">
              <label htmlFor="staff-order-type">Tipo</label>
              <select id="staff-order-type" name="order_type" defaultValue="para_llevar">
                <option value="para_llevar">Para llevar</option>
                <option value="en_mesa">En mesa</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="staff-table">Mesa</label>
              <input id="staff-table" name="table_number" type="number" min={1} max={999} />
            </div>
          </div>
          <fieldset className="menu-picker">
            <legend>Productos y cantidades</legend>
            {menu.map((item) => (
              <label key={item.product_id} className="menu-picker-row">
                <span>
                  <strong>{item.name}</strong>
                  <span className="muted"> · {money(item.price)}</span>
                </span>
                <input
                  type="number"
                  name={`qty_${item.product_id}`}
                  min={0}
                  max={50}
                  defaultValue={0}
                  aria-label={`Cantidad de ${item.name}`}
                />
              </label>
            ))}
          </fieldset>
          <div className="field">
            <label htmlFor="staff-notes">Notas</label>
            <textarea id="staff-notes" name="notes" rows={2} maxLength={500} />
          </div>
          <button className="button" type="submit">
            Crear pedido
          </button>
        </form>

        <section className="card">
          <div className="section-heading">
            <h2>Pedidos del turno</h2>
            <div className="filter-links">
              <Link href="/staff/orders">Todos</Link>
              <Link href="/staff/orders?status=pendiente">Pendientes</Link>
              <Link href="/staff/orders?status=pagado">Pagados</Link>
              <Link href="/staff/orders?status=listo">Listos</Link>
            </div>
          </div>
          {orders.length === 0 ? (
            <EmptyState title="Sin pedidos" description="Aún no hay pedidos con este filtro." />
          ) : (
            <ul className="stack-list">
              {orders.map((order) => (
                <li className="stack-item" key={order.id}>
                  <div>
                    <strong>
                      <Link href={`/orders/${order.id}`}>{order.id.slice(0, 8)}</Link>
                    </strong>
                    <div className="muted">
                      {order.order_type === 'en_mesa'
                        ? `Mesa ${order.table_number ?? ''}`
                        : 'Para llevar'}{' '}
                      · {money(order.total)}
                    </div>
                    <div className="muted">
                      {order.items.map((item) => `${item.quantity}× ${item.product_name}`).join(' · ')}
                    </div>
                  </div>
                  <div className="stack-actions">
                    <StatusBadge status={order.status} />
                    {canCharge && order.status === 'pendiente' ? (
                      <form action={payOrder}>
                        <input type="hidden" name="order_id" value={order.id} />
                        <input type="hidden" name="return_to" value="/staff/orders" />
                        <button className="button" type="submit">
                          Cobrar
                        </button>
                      </form>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  );
}
