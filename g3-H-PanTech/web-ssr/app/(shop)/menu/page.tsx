import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { addToCart } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { readCart } from '@/lib/cart';
import { money } from '@/lib/format';
import { isStaff } from '@/lib/permissions';
import type { MenuItem } from '@/lib/types';

export const metadata = { title: 'Menú' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function MenuPage({ searchParams }: Props) {
  const session = requireSession();
  const cart = readCart();
  const count = Object.values(cart).reduce((total, qty) => total + qty, 0);

  let items: MenuItem[] = [];
  let error: string | undefined;
  try {
    items = await apiFetch<MenuItem[]>('/menu');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el menú.';
  }

  const grouped = new Map<string, MenuItem[]>();
  for (const item of items) {
    const list = grouped.get(item.category) ?? [];
    list.push(item);
    grouped.set(item.category, list);
  }

  return (
    <main>
      <PageHeader
        eyebrow="Tienda"
        title="Menú de la panadería"
        description="Precios y tiempos de preparación calculados por el servidor. Agrega productos al carrito y paga en línea."
        actions={
          <>
            {isStaff(session) ? (
              <Link className="button button-secondary" href="/staff">
                Backoffice
              </Link>
            ) : null}
            <Link className="button" href="/cart">
              Carrito ({count})
            </Link>
          </>
        }
      />
      <Alert error={value(searchParams?.error)} ok={value(searchParams?.ok)} />

      {error ? <p className="form-error">{error}</p> : null}
      {!error && items.length === 0 ? (
        <EmptyState
          title="El menú está vacío"
          description="Un administrador debe cargar productos desde el backoffice."
        />
      ) : null}

      {Array.from(grouped.entries()).map(([category, products]) => (
        <section key={category} className="catalog-section">
          <h2>{category === 'vitrina' ? 'Vitrina' : 'Preparados en cocina'}</h2>
          <div className="catalog-grid">
            {products.map((item) => (
              <article className="product-card" key={item.product_id}>
                <div>
                  <h3>{item.name}</h3>
                  <p className="muted">
                    {item.prep_minutes === 0
                      ? 'Listo de inmediato'
                      : `${item.prep_minutes} min de preparación`}
                  </p>
                </div>
                <p className="product-price">{money(item.price)}</p>
                <form action={addToCart} className="quick-add">
                  <input type="hidden" name="product_id" value={item.product_id} />
                  <label>
                    <span className="sr-only">Cantidad</span>
                    <input
                      type="number"
                      name="quantity"
                      min={1}
                      max={50}
                      defaultValue={1}
                      aria-label={`Cantidad de ${item.name}`}
                    />
                  </label>
                  <button className="button" type="submit">
                    Agregar
                  </button>
                </form>
              </article>
            ))}
          </div>
        </section>
      ))}
    </main>
  );
}
