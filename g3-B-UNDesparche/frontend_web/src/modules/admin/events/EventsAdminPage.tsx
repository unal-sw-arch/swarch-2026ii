import React, { useState, useMemo } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { useAdminEvents } from '../../../api/hooks/useEvents';
import { CampusEvent, CreateEventPayload, UpdateEventPayload } from '../../../types/event';
import { EventTable } from './components/EventTable';
import { EventFormModal } from './components/EventFormModal';
import { Pagination } from '../../../shared/components/ui/Pagination';
import { ConfirmDialog } from '../../../shared/components/ui/ConfirmDialog';
import { LoadingSpinner } from '../../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../../shared/components/ui/ErrorAlert';

export function EventsAdminPage(): React.JSX.Element {
  const { profile, token } = useAuth();
  const {
    events,
    loading,
    saving,
    error,
    createEvent,
    updateEvent,
    deleteEvent,
    publishEvent,
    refresh,
    clearError,
  } = useAdminEvents(token);

  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CampusEvent | null>(null);

  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [eventToDelete, setEventToDelete] = useState<CampusEvent | null>(null);
  const [publishingId, setPublishingId] = useState<number | null>(null);

  // Paginación 0-indexed
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Filtro: para el Administrador de Eventos, solo sus propios eventos (organizer.id === profile.id)
  const myEvents = useMemo(() => {
    if (!profile) return [];
    return events.filter((ev) => ev.organizer?.id === profile.id);
  }, [events, profile]);

  const filteredEvents = useMemo(() => {
    return myEvents.filter((ev) => {
      if (!search.trim()) return true;
      const q = search.toLowerCase();
      return (
        ev.name.toLowerCase().includes(q) ||
        ev.place.toLowerCase().includes(q) ||
        ev.description.toLowerCase().includes(q)
      );
    });
  }, [myEvents, search]);

  // Reset de página al cambiar búsqueda
  const handleSearchChange = (newSearch: string) => {
    setSearch(newSearch);
    setPage(0);
  };

  const paginatedEvents = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredEvents.slice(start, start + rowsPerPage);
  }, [filteredEvents, page, rowsPerPage]);

  const handleOpenCreate = () => {
    setEditingEvent(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (ev: CampusEvent) => {
    setEditingEvent(ev);
    setModalOpen(true);
  };

  const handleFormSubmit = async (payload: CreateEventPayload | UpdateEventPayload) => {
    if (editingEvent) {
      await updateEvent(editingEvent.id, payload as UpdateEventPayload);
    } else {
      await createEvent(payload as CreateEventPayload);
    }
  };

  const handleOpenDelete = (ev: CampusEvent) => {
    setEventToDelete(ev);
    setDeleteConfirmOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!eventToDelete) return;
    try {
      await deleteEvent(eventToDelete.id);
      setDeleteConfirmOpen(false);
      setEventToDelete(null);
    } catch {
      // Error manejado en hook
    }
  };

  const handlePublish = async (ev: CampusEvent) => {
    setPublishingId(ev.id);
    try {
      await publishEvent(ev.id);
    } catch {
      // Error en hook
    } finally {
      setPublishingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Encabezado */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="font-display font-bold text-2xl text-gray-900 dark:text-white">
            Gestión de Mis Eventos
          </h1>
          <p className="text-xs text-gray-500">
            Crea, edita y publica las actividades a tu cargo como Administrador de Eventos.
          </p>
        </div>

        <button
          type="button"
          onClick={handleOpenCreate}
          className="heroui-btn-solid inline-flex w-full sm:w-auto items-center justify-center gap-2 px-4 py-2.5 font-bold text-xs focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-lg" aria-hidden="true">
            add
          </span>
          Crear Nuevo Evento
        </button>
      </div>

      {error && !modalOpen && (
        <ErrorAlert
          title="Error en la gestión de eventos"
          message={error.message}
          onRetry={refresh}
        />
      )}

      {/* Barra de búsqueda */}
      <div className="heroui-card flex items-center p-3 shadow-xs">
        <label htmlFor="admin-events-search" className="sr-only">
          Buscar mis eventos
        </label>
        <span className="material-symbols-outlined pl-2 text-neutral-400" aria-hidden="true">
          search
        </span>
        <input
          id="admin-events-search"
          type="search"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          placeholder="Buscar en mis eventos por nombre, lugar o descripción…"
          className="w-full bg-transparent px-3 py-1.5 text-sm text-neutral-900 placeholder-gray-400 focus:outline-hidden dark:text-white"
        />
        {search && (
          <button
            type="button"
            onClick={() => handleSearchChange('')}
            aria-label="Limpiar búsqueda"
            className="pr-2 text-neutral-400 hover:text-neutral-600 dark:hover:text-white"
          >
            <span className="material-symbols-outlined text-base">close</span>
          </button>
        )}
      </div>

      {/* Tabla de Eventos */}
      {loading ? (
        <div className="py-16">
          <LoadingSpinner label="Cargando listado de eventos…" />
        </div>
      ) : (
        <div className="space-y-4">
          <EventTable
            events={paginatedEvents}
            showOrganizer={false}
            onEdit={handleOpenEdit}
            onDelete={handleOpenDelete}
            onPublish={handlePublish}
            publishingId={publishingId}
          />

          <Pagination
            count={filteredEvents.length}
            page={page}
            rowsPerPage={rowsPerPage}
            onPageChange={setPage}
            onRowsPerPageChange={(newRows) => {
              setRowsPerPage(newRows);
              setPage(0);
            }}
          />
        </div>
      )}

      {/* Modal de Crear / Editar */}
      <EventFormModal
        open={modalOpen}
        eventToEdit={editingEvent}
        onClose={() => {
          setModalOpen(false);
          clearError();
        }}
        onSubmit={handleFormSubmit}
        saving={saving}
      />

      {/* Modal de Confirmación de Borrado */}
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="¿Eliminar este evento?"
        description={`Esta acción eliminará permanentemente "${eventToDelete?.name}". ${
          eventToDelete?.published
            ? 'Ten en cuenta que si el evento está publicado, la eliminación es irreversible.'
            : ''
        }`}
        confirmLabel="Eliminar evento"
        cancelLabel="Cancelar"
        danger
        pending={saving}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
