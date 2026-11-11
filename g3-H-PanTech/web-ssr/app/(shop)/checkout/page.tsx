import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { createOrder } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { readCart } from '@/lib/cart';
import { cents, fromCents, money } from '@/lib/format';
import { can } from '@/lib/permissions';
import type { MenuItem } from '@/lib/types';

export const metadata = { title: 'Checkout' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function CheckoutPage({ searchParams }: Props) {
  const session = requireSession();
  const cart = readCart();

  let menu: MenuItem[] = [];
  let error: string | undefined;
  try {
    menu = await apiFetch<MenuItem[]>('/menu');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el menú.';
  }

  const byId = new Map(menu.map((item) => [item.product_id, item]));
  const lines = Object.entries(cart)
    .map(([productId, quantity]) => {
      const product = byId.get(productId);
      if (!product) return null;
      return { product, quantity, lineTotalCents: cents(product.price) * quantity };
    })
    .filter((line): line is NonNullable<typeof line> => line !== null);
  const totalCents = lines.reduce((total, line) => total + line.lineTotalCents, 0);
  const staff = can(session, 'orders:create');

  return (
    <main>
      <PageHeader
        eyebrow="Compra"
        title="Confirmar pedido"
        description="Revisa el resumen y elige cómo recibir tu pedido. El pago se confirma después de crear la orden."
        actions={
          <Link className="button button-secondary" href="/cart">
            Volver al carrito
          </Link>
        }
      />
      <Alert error={value(searchParams?.error)} ok={value(searchParams?.ok)} />
      {error ? <p className="form-error">{error}</p> : null}

      {lines.length === 0 ? (
        <EmptyState
          title="No hay productos para pagar"
          description="Agrega productos al carrito antes de hacer checkout."
          action={<Link className="button" href="/menu">Ver el menú</Link>}
        />
      ) : (
        <div className="cart-layout">
          <form action={createOrder} className="card form">
            <h2>Datos del pedido</h2>
            {staff ? (
              <div className="field">
                <label htmlFor="checkout-order-type">Tipo de pedido</label>
                <select id="checkout-order-type" name="order_type" defaultValue="para_llevar">
                  <option value="para_llevar">Para llevar</option>
                  <option value="en_mesa">En mesa</option>
                </select>
              </div>
            ) : (
              <input type="hidden" name="order_type" value="para_llevar" />
            )}
            {staff ? (
              <div className="field">
                <label htmlFor="checkout-table">Número de mesa (solo si es en mesa)</label>
                <input id="checkout-table" name="table_number" type="number" min={1} max={999} />
              </div>
            ) : null}
            <div className="field">
              <label htmlFor="checkout-notes">Notas</label>
              <textarea id="checkout-notes" name="notes" maxLength={500} rows={3} />
            </div>
            <button className="button" type="submit">
              Crear pedido y continuar al pago
            </button>
          </form>

          <aside className="card checkout-summary">
            <h2>Resumen</h2>
            <ul className="summary-list">
              {lines.map(({ product, quantity, lineTotalCents }) => (
                <li key={product.product_id}>
                  <span>
                    {quantity}× {product.name}
                  </span>
                  <strong>{money(fromCents(lineTotalCents))}</strong>
                </li>
              ))}
            </ul>
            <dl>
              <div>
                <dt>Total calculado</dt>
                <dd className="total-amount">{money(fromCents(totalCents))}</dd>
              </div>
            </dl>
            <p className="muted">
              El monto se recalcula en el servidor al crear el pedido y se vuelve a validar al
              pagar.
            </p>
          </aside>
        </div>
      )}
    </main>
  );
}
