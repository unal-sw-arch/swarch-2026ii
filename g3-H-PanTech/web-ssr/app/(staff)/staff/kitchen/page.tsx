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
  const session = requirePermission('kitchen:read');
  const canUpdate = session.permissions.includes('kitchen:update');
  const canDeliver = session.permissions.includes('kitchen:deliver');
  const dispatchOnly =
    canDeliver && !canUpdate;

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
        eyebrow={dispatchOnly ? 'Despacho' : 'Cocina / KDS'}
        title={dispatchOnly ? 'Pedidos listos para entregar' : 'Cola priorizada'}
        description={
          dispatchOnly
            ? 'Solo aparecen pedidos marcados como listos. Confirma su entrega desde esta vista o desde Flutter KDS/POS.'
            : 'Actualiza los estados de preparación desde esta vista. La app Flutter KDS/POS también permite operar la cola.'
        }
        actions={
          <>
            <Link className="button button-secondary" href="/staff">
              Volver al panel
            </Link>
            {session.permissions.includes('orders:create') ||
            session.permissions.includes('orders:charge') ? (
              <Link className="button button-secondary" href="/staff/orders">
                Pedidos
              </Link>
            ) : null}
          </>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />
      <KitchenBoard
        initial={queue}
        dispatchOnly={dispatchOnly}
        canUpdate={canUpdate}
        canDeliver={canDeliver}
      />
    </main>
  );
}
