import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import { createProduct } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import { money } from '@/lib/format';
import type { Product } from '@/lib/types';

export const metadata = { title: 'Productos' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function ProductsPage({ searchParams }: Props) {
  requirePermission('products:manage');

  let products: Product[] = [];
  let error: string | undefined;
  try {
    products = await apiFetch<Product[]>('/products');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el catálogo.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Administración"
        title="Catálogo de productos"
        description="Crea productos, ajusta precios y tiempos de preparación o retíralos del menú sin borrar su historial."
        actions={
          <Link className="button button-secondary" href="/staff">
            Volver al panel
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <form action={createProduct} className="inline-form card">
        <label>
          Nombre
          <input name="name" required maxLength={120} placeholder="Pan de queso" />
        </label>
        <label>
          Categoría
          <select name="category" defaultValue="vitrina">
            <option value="vitrina">Vitrina</option>
            <option value="preparado">Preparado</option>
          </select>
        </label>
        <label>
          Precio
          <input name="price" type="number" min={0} step="0.01" required placeholder="2.50" />
        </label>
        <label>
          Minutos
          <input name="prep_minutes" type="number" min={0} max={600} defaultValue={1} required />
        </label>
        <button className="button" type="submit">
          Crear producto
        </button>
      </form>

      {products.length === 0 ? (
        <EmptyState title="Catálogo vacío" description="Crea el primer producto con el formulario." />
      ) : (
        <div className="table-wrap card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Categoría</th>
                <th>Precio</th>
                <th>Preparación</th>
                <th>Estado</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.product_id}>
                  <td>
                    <strong>{product.name}</strong>
                    <div className="muted">
                      <code>{product.product_id}</code>
                    </div>
                  </td>
                  <td>{product.category === 'vitrina' ? 'Vitrina' : 'Preparado'}</td>
                  <td>{money(product.price)}</td>
                  <td>{product.prep_minutes} min</td>
                  <td>
                    <span className={product.active ? 'badge badge-ok' : 'badge badge-danger'}>
                      {product.active ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>
                    <Link href={`/staff/products/${product.product_id}`}>Editar</Link>
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
