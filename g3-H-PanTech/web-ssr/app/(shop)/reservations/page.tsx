import Alert from '@/components/Alert';
import EmptyState from '@/components/EmptyState';
import PageHeader from '@/components/PageHeader';
import StatusBadge from '@/components/StatusBadge';
import { changeReservationStatus, createReservation } from '@/lib/actions';
import { apiFetch, ApiError } from '@/lib/api';
import { requireSession } from '@/lib/auth';
import { formatDate, todayISO } from '@/lib/format';
import type { Reservation } from '@/lib/types';

export const metadata = { title: 'Reservas' };
export const dynamic = 'force-dynamic';

type Props = {
  searchParams?: { error?: string | string[]; ok?: string | string[] };
};

function value(input?: string | string[]): string | undefined {
  return typeof input === 'string' ? input : undefined;
}

export default async function ReservationsPage({ searchParams }: Props) {
  requireSession();
  let reservations: Reservation[] = [];
  let error: string | undefined;
  try {
    reservations = await apiFetch<Reservation[]>('/reservations?limit=100');
  } catch (caught) {
    error = caught instanceof ApiError ? caught.message : 'No se pudieron cargar las reservas.';
  }

  return (
    <main>
      <PageHeader
        eyebrow="Reservas"
        title="Tus reservas"
        description="Crea una reserva y cancélala cuando lo necesites. El personal confirma la disponibilidad desde el backoffice."
      />
      <Alert error={value(searchParams?.error) ?? error} ok={value(searchParams?.ok)} />

      <div className="two-column">
        <form action={createReservation} className="card form">
          <h2>Nueva reserva</h2>
          <div className="field">
            <label htmlFor="reservation-date">Fecha</label>
            <input id="reservation-date" name="date" type="date" defaultValue={todayISO()} required />
          </div>
          <div className="field">
            <label htmlFor="reservation-time">Hora</label>
            <input id="reservation-time" name="time" type="time" defaultValue="10:00" required />
          </div>
          <div className="field">
            <label htmlFor="reservation-people">Personas</label>
            <input
              id="reservation-people"
              name="people"
              type="number"
              min={1}
              max={20}
              defaultValue={2}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="reservation-notes">Notas</label>
            <textarea id="reservation-notes" name="notes" rows={3} maxLength={500} />
          </div>
          <button className="button" type="submit">
            Solicitar reserva
          </button>
        </form>

        <section className="card">
          <h2>Historial de reservas</h2>
          {reservations.length === 0 ? (
            <EmptyState
              title="Sin reservas"
              description="Cuando crees una reserva aparecerá aquí."
            />
          ) : (
            <ul className="stack-list">
              {reservations.map((reservation) => (
                <li key={reservation.id} className="stack-item">
                  <div>
                    <strong>
                      {formatDate(reservation.date)} · {reservation.time.slice(0, 5)}
                    </strong>
                    <div className="muted">
                      {reservation.people} persona{reservation.people === 1 ? '' : 's'}
                      {reservation.notes ? ` · ${reservation.notes}` : ''}
                    </div>
                  </div>
                  <div className="stack-actions">
                    <StatusBadge status={reservation.status} />
                    {reservation.status !== 'cancelada' ? (
                      <form action={changeReservationStatus}>
                        <input type="hidden" name="reservation_id" value={reservation.id} />
                        <input type="hidden" name="status" value="cancelada" />
                        <input type="hidden" name="return_to" value="/reservations" />
                        <button className="link-button" type="submit">
                          Cancelar
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
