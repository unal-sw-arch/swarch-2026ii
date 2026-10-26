import React, { useState, useMemo } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { useEvents } from '../../api/hooks/useEvents';
import { CampusEvent, EVENT_CATEGORY_LABELS } from '../../types/event';
import { CampusMap } from './components/CampusMap';
import { LoadingSpinner } from '../../shared/components/ui/LoadingSpinner';

export function CampusMapPage(): React.JSX.Element {
  const { token } = useAuth();
  const { events, loading } = useEvents(token);

  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [selectedEvent, setSelectedEvent] = useState<CampusEvent | null>(null);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  // Filtrado local en el mapa para respuesta instantánea
  const filteredEvents = useMemo(() => {
    return events.filter((ev) => {
      const matchesSearch =
        !search.trim() ||
        ev.name.toLowerCase().includes(search.toLowerCase()) ||
        ev.place.toLowerCase().includes(search.toLowerCase()) ||
        ev.description.toLowerCase().includes(search.toLowerCase());

      const matchesCategory =
        !selectedCategory || ev.category === selectedCategory;

      return matchesSearch && matchesCategory;
    });
  }, [events, search, selectedCategory]);

  const sidebarListContent = (
    <div className="flex h-full flex-col bg-white/90 dark:bg-[#080E14] backdrop-blur-xl">
      {/* Buscador de mapa */}
      <div className="border-b border-[#00D592]/15 p-4 dark:border-gray-800">
        <label htmlFor="map-search-input" className="sr-only">
          Buscar evento en el mapa
        </label>
        <div className="relative">
          <span
            className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-[#00D592]"
            aria-hidden="true"
          >
            <span className="material-symbols-outlined text-xl">search</span>
          </span>
          <input
            id="map-search-input"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por lugar o nombre…"
            className="w-full rounded-2xl border border-gray-200/90 bg-white/80 py-2.5 pr-4 pl-10.5 text-sm placeholder-gray-400 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#111A24] dark:text-white transition-all"
          />
        </div>

        {/* Chips de categoría con HeroUI Mint */}
        <div className="mt-3.5 flex flex-wrap gap-1.5" role="group" aria-label="Filtro por categoría">
          <button
            type="button"
            aria-pressed={selectedCategory === ''}
            onClick={() => setSelectedCategory('')}
            className={`rounded-full px-3 py-1 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-[#00D592] ${
              selectedCategory === ''
                ? 'bg-[#00D592] text-[#032B1D] shadow-xs'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-[#111A24] dark:text-gray-300'
            }`}
          >
            Todas
          </button>
          {Object.entries(EVENT_CATEGORY_LABELS).map(([catKey, label]) => {
            const isPressed = selectedCategory === catKey;
            return (
              <button
                key={catKey}
                type="button"
                aria-pressed={isPressed}
                onClick={() => setSelectedCategory(isPressed ? '' : catKey)}
                className={`rounded-full px-3 py-1 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-[#00D592] ${
                  isPressed
                    ? 'bg-[#00D592] text-[#032B1D] shadow-xs'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-[#111A24] dark:text-gray-300'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Lista de eventos scrollable */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        <p className="text-xs font-bold text-[#00875A] dark:text-[#00F5A0] uppercase tracking-wider">
          {filteredEvents.length} {filteredEvents.length === 1 ? 'actividad encontrada' : 'actividades encontradas'}
        </p>

        {filteredEvents.length === 0 ? (
          <div className="py-8 text-center text-xs text-gray-500">
            No hay eventos en esta área o categoría.
          </div>
        ) : (
          filteredEvents.map((ev) => {
            const isSelected = selectedEvent?.id === ev.id;
            return (
              <article
                key={ev.id}
                className={`heroui-card p-3.5 transition-all ${
                  isSelected
                    ? 'border-[#00D592] bg-[#00D592]/10 shadow-md ring-1 ring-[#00D592]/40'
                    : 'hover:border-[#00D592]/40'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="heroui-chip text-[10px] py-0.5 px-2">
                    {ev.category ? EVENT_CATEGORY_LABELS[ev.category] : 'Evento'}
                  </span>
                  <Link
                    to={`/events/${ev.id}`}
                    className="text-xs font-bold text-[#00875A] hover:text-[#00BA7C] dark:text-[#00F5A0]"
                  >
                    Detalles →
                  </Link>
                </div>

                <h3 className="mt-2 font-display font-bold text-sm text-gray-900 line-clamp-1 dark:text-white">
                  {ev.name}
                </h3>
                <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400 line-clamp-1 flex items-center gap-1">
                  <span className="material-symbols-outlined text-xs text-[#00D592]">location_on</span>
                  {ev.place}
                </p>

                <div className="mt-3 flex items-center justify-between pt-2 border-t border-[#00D592]/15 dark:border-gray-800">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedEvent(ev);
                      setMobileDrawerOpen(false);
                    }}
                    className={`rounded-full px-3 py-1 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-[#00D592] ${
                      isSelected
                        ? 'heroui-btn-solid'
                        : 'heroui-btn-flat'
                    }`}
                  >
                    {isSelected ? 'Ubicado en mapa' : 'Centrar en mapa'}
                  </button>
                </div>
              </article>
            );
          })
        )}
      </div>
    </div>
  );

  return (
    <div className="relative flex h-[calc(100vh-4rem)] flex-col md:flex-row font-sans">
      {/* Sidebar en Desktop (340px) */}
      <aside className="hidden w-84 shrink-0 border-r border-[#00D592]/15 md:block dark:border-gray-800">
        {sidebarListContent}
      </aside>

      {/* FAB flotante para móvil */}
      <div className="absolute bottom-5 left-1/2 z-30 -translate-x-1/2 md:hidden">
        <button
          type="button"
          onClick={() => setMobileDrawerOpen(true)}
          className="heroui-btn-solid inline-flex items-center gap-2 px-6 py-3 text-sm font-bold shadow-xl"
        >
          <span className="material-symbols-outlined text-lg" aria-hidden="true">
            list
          </span>
          Ver lista de eventos ({filteredEvents.length})
        </button>
      </div>

      {/* Panel deslizante para móvil */}
      {mobileDrawerOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden" role="dialog" aria-modal="true" aria-label="Lista de eventos del campus">
          <div
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
            onClick={() => setMobileDrawerOpen(false)}
            aria-hidden="true"
          />
          <div className="relative z-10 flex h-full w-full max-w-sm flex-col bg-white shadow-2xl dark:bg-[#080E14]">
            <div className="flex h-14 items-center justify-between border-b border-[#00D592]/15 px-4 dark:border-gray-800">
              <span className="font-display font-bold text-sm">Eventos geolocalizados</span>
              <button
                type="button"
                onClick={() => setMobileDrawerOpen(false)}
                aria-label="Cerrar lista"
                className="rounded-full p-1.5 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800"
              >
                <span className="material-symbols-outlined text-xl" aria-hidden="true">
                  close
                </span>
              </button>
            </div>
            <div className="flex-1 overflow-hidden">
              {sidebarListContent}
            </div>
          </div>
        </div>
      )}

      {/* Área del Mapa */}
      <section className="relative flex-1" aria-label="Mapa interactivo del campus">
        {loading ? (
          <div className="flex h-full items-center justify-center">
            <LoadingSpinner label="Cargando mapa y puntos de interés…" />
          </div>
        ) : (
          <CampusMap
            events={filteredEvents}
            selectedEvent={selectedEvent}
            onSelectEvent={setSelectedEvent}
          />
        )}
      </section>
    </div>
  );
}
