import Link from 'next/link';
import Alert from '@/components/Alert';
import KitchenBoard from '@/components/KitchenBoard';
import PageHeader from '@/components/PageHeader';
import { apiFetch, ApiError } from '@/lib/api';
import { requirePermission } from '@/lib/auth';
import type { QueueResponse } from '@/lib/types';

export const metadata = { title: 'Cola de cocina' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function KitchenPage({ searchParams }: Props) {
  requirePermission('kitchen:read');

  let queue: QueueResponse = {
    items: [],
    capacity: { max: 0, in_preparation: 0 },
    generated_at: new Date().toISOString(),
  };
  let error: string | undefined;
  try {
    queue = await apiFetch<QueueResponse>('/queue');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudo cargar la cola de cocina.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Cocina / KDS"
        title="Cola priorizada"
        description="Vista web de solo lectura. La app Flutter KDS/POS sigue siendo el cliente principal para cocineros; esta vista sirve como respaldo cuando no está instalada."
        actions={
          <>
            <Link className="button button-secondary" href="/staff">
              Volver al panel
            </Link>
            <Link className="button button-secondary" href="/staff/orders">
              Pedidos
            </Link>
          </>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />
      <KitchenBoard initial={queue} />
    </main>
  );
}
