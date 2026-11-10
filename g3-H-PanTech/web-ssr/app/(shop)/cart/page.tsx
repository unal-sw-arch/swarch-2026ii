import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { removeCartLine, updateCartLine } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { readCart } from '@/lib/cart';
import { cents, fromCents, money } from '@/lib/format';
import type { MenuItem } from '@/lib/types';

export const metadata = { title: 'Carrito' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function CartPage({ searchParams }: Props) {
  requireSession();
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
      return {
        product,
        quantity,
        lineTotalCents: cents(product.price) * quantity,
      };
    })
    .filter((line): line is NonNullable<typeof line> => line !== null);
  const totalCents = lines.reduce((total, line) => total + line.lineTotalCents, 0);

  return (
    <main>
      <PageHeader
        eyebrow="Compra"
        title="Tu carrito"
        description="El total lo calcula el servidor con el catálogo vigente. Al pagar, Order Service vuelve a validar cada precio."
        actions={
          <Link className="button button-secondary" href="/menu">
            Seguir comprando
          </Link>
        }
      />
      <Alert error={value(searchParams?.error)} ok={value(searchParams?.ok)} />
      {error ? <p className="form-error">{error}</p> : null}

      {lines.length === 0 ? (
        <EmptyState
          title="El carrito está vacío"
          description="Agrega productos del menú para preparar tu pedido."
          action={<Link className="button" href="/menu">Ver el menú</Link>}
        />
      ) : (
        <div className="cart-layout">
          <section className="card" aria-labelledby="cart-lines-title">
            <h2 id="cart-lines-title">Productos</h2>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Precio</th>
                    <th>Cantidad</th>
                    <th>Subtotal</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {lines.map(({ product, quantity, lineTotalCents }) => (
                    <tr key={product.product_id}>
                      <td>
                        <strong>{product.name}</strong>
                        <div className="muted">
                          {product.prep_minutes} min de preparación
                        </div>
                      </td>
                      <td>{money(product.price)}</td>
                      <td>
                        <form action={updateCartLine} className="qty-form">
                          <input type="hidden" name="product_id" value={product.product_id} />
                          <input
                            type="number"
                            name="quantity"
                            min={1}
                            max={50}
                            defaultValue={quantity}
                            aria-label={`Cantidad de ${product.name}`}
                          />
                          <button className="button button-secondary" type="submit">
                            Actualizar
                          </button>
                        </form>
                      </td>
                      <td>{money(fromCents(lineTotalCents))}</td>
                      <td>
                        <form action={removeCartLine}>
                          <input type="hidden" name="product_id" value={product.product_id} />
                          <button className="link-button" type="submit">
                            Quitar
                          </button>
                        </form>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <aside className="card checkout-summary">
            <h2>Resumen</h2>
            <dl>
              <div>
                <dt>Productos</dt>
                <dd>{lines.reduce((total, line) => total + line.quantity, 0)}</dd>
              </div>
              <div>
                <dt>Total</dt>
                <dd className="total-amount">{money(fromCents(totalCents))}</dd>
              </div>
            </dl>
            <Link className="button" href="/checkout">
              Ir al checkout
            </Link>
          </aside>
        </div>
      )}
    </main>
  );
}
