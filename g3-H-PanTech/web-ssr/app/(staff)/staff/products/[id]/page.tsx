import Link from 'next/link';
import { notFound } from 'next/navigation';
import Alert from '@/components/Alert';
import PageHeader from '@/components/PageHeader';
import { deleteProduct, updateProduct } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import type { Product } from '@/lib/types';

export const metadata = { title: 'Editar producto' };
export const dynamic = 'force-dynamic';

type Props = {
  params: { id: string };
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function EditProductPage({ params, searchParams }: Props) {
  requirePermission('products:manage');

  let product: Product | null = null;
  let error: string | undefined;
  try {
    product = await apiFetch<Product>(`/products/${encodeURIComponent(params.id)}`);
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) notFound();
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar el producto.';
  }

  if (!product) {
    return (
      <main>
        <PageHeader title="Producto no disponible" />
        <Alert error={error} />
      </main>
    );
  }

  return (
    <main>
      <PageHeader
        eyebrow="Catálogo"
        title={product.name}
        description={`Identificador: ${product.product_id}`}
        actions={
          <Link className="button button-secondary" href="/staff/products">
            Volver al catálogo
          </Link>
        }
      />
      <Alert error={value(searchParams?.error)} ok={value(searchParams?.ok)} />

      <div className="two-column">
        <form action={updateProduct} className="card form">
          <h2>Editar</h2>
          <input type="hidden" name="product_id" value={product.product_id} />
          <div className="field">
            <label htmlFor="product-name">Nombre</label>
            <input id="product-name" name="name" defaultValue={product.name} required maxLength={120} />
          </div>
          <div className="field">
            <label htmlFor="product-category">Categoría</label>
            <select id="product-category" name="category" defaultValue={product.category}>
              <option value="vitrina">Vitrina</option>
              <option value="preparado">Preparado</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="product-price">Precio</label>
            <input
              id="product-price"
              name="price"
              type="number"
              min={0}
              step="0.01"
              defaultValue={product.price}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="product-prep">Minutos de preparación</label>
            <input
              id="product-prep"
              name="prep_minutes"
              type="number"
              min={0}
              max={600}
              defaultValue={product.prep_minutes}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="product-active">Estado</label>
            <select id="product-active" name="active" defaultValue={product.active ? 'true' : 'false'}>
              <option value="true">Activo</option>
              <option value="false">Inactivo</option>
            </select>
          </div>
          <button className="button" type="submit">
            Guardar cambios
          </button>
        </form>

        <form action={deleteProduct} className="card form danger-zone">
          <h2>Retirar producto</h2>
          <p className="muted">
            Si el producto ya tiene pedidos, Order Service responderá 409 y no lo borrará. En ese
            caso márcalo como inactivo para conservar el historial.
          </p>
          <input type="hidden" name="product_id" value={product.product_id} />
          <button className="button button-danger" type="submit">
            Eliminar definitivamente
          </button>
        </form>
      </div>
    </main>
  );
}
