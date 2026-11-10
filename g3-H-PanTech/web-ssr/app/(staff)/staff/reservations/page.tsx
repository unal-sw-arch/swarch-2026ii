import Link from 'next/link';
import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { changeReservationStatus } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireStaff } from '@/lib/auth';
import { formatDate } from '@/lib/format';
import type { Reservation } from '@/lib/types';

export const metadata = { title: 'Reservas del personal' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: {
    error?: string | string[];
    ok?: string | string[];
    status?: string | string[];
    from?: string | string[];
    to?: string | string[];
  };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function StaffReservationsPage({ searchParams }: Props) {
  requireStaff();
  const query = new URLSearchParams({ limit: '100' });
  for (const key of ['status', 'from', 'to'] as const) {
    const current = value(searchParams?.[key]);
    if (current) query.set(key === 'from' ? 'date_from' : key === 'to' ? 'date_to' : key, current);
  }

  let reservations: Reservation[] = [];
  let error: string | undefined;
  try {
    reservations = await apiFetch<Reservation[]>(`/reservations?${query.toString()}`);
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar las reservas.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Mesero / supervisor"
        title="Reservas del turno"
        description="Confirma o cancela reservas. El cliente solo puede gestionar las suyas; el personal con reservations:manage_all ve todas."
        actions={
          <Link className="button button-secondary" href="/staff">
            Volver al panel
          </Link>
        }
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <form className="inline-form card" method="get">
        <label>
          Estado
          <select name="status" defaultValue={value(searchParams?.status) ?? ''}>
            <option value="">Todos</option>
            <option value="pendiente">Pendientes</option>
            <option value="confirmada">Confirmadas</option>
            <option value="cancelada">Canceladas</option>
          </select>
        </label>
        <label>
          Desde
          <input type="date" name="from" defaultValue={value(searchParams?.from) ?? ''} />
        </label>
        <label>
          Hasta
          <input type="date" name="to" defaultValue={value(searchParams?.to) ?? ''} />
        </label>
        <button className="button button-secondary" type="submit">
          Filtrar
        </button>
      </form>

      {reservations.length === 0 ? (
        <EmptyState title="Sin reservas" description="No hay reservas con los filtros actuales." />
      ) : (
        <div className="table-wrap card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Hora</th>
                <th>Personas</th>
                <th>Cliente</th>
                <th>Estado</th>
                <th>Notas</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {reservations.map((reservation) => (
                <tr key={reservation.id}>
                  <td>{formatDate(reservation.date)}</td>
                  <td>{reservation.time.slice(0, 5)}</td>
                  <td>{reservation.people}</td>
                  <td>
                    <code>{reservation.customer_id.slice(0, 8)}</code>
                  </td>
                  <td>
                    <StatusBadge status={reservation.status} />
                  </td>
                  <td>{reservation.notes || '—'}</td>
                  <td>
                    <div className="row-actions">
                      {reservation.status === 'pendiente' ? (
                        <form action={changeReservationStatus}>
                          <input type="hidden" name="reservation_id" value={reservation.id} />
                          <input type="hidden" name="status" value="confirmada" />
                          <input type="hidden" name="return_to" value="/staff/reservations" />
                          <button className="button" type="submit">
                            Confirmar
                          </button>
                        </form>
                      ) : null}
                      {reservation.status !== 'cancelada' ? (
                        <form action={changeReservationStatus}>
                          <input type="hidden" name="reservation_id" value={reservation.id} />
                          <input type="hidden" name="status" value="cancelada" />
                          <input type="hidden" name="return_to" value="/staff/reservations" />
                          <button className="link-button" type="submit">
                            Cancelar
                          </button>
                        </form>
                      ) : null}
                    </div>
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
