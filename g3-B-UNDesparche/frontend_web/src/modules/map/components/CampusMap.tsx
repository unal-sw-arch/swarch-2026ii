import React, { useEffect } from 'react';
import { Link } from 'react-router';
import {
  APIProvider,
  Map,
  Marker,
  InfoWindow,
  useMap,
} from '@vis.gl/react-google-maps';
import { CampusEvent, EVENT_CATEGORY_LABELS } from '../../../types/event';

export interface CampusMapProps {
  events: CampusEvent[];
  selectedEvent: CampusEvent | null;
  onSelectEvent: (event: CampusEvent | null) => void;
}

const CATEGORY_COLORS: Record<string, string> = {
  ACA: '#003865',
  CUL: '#FF9800',
  DEP: '#C62828',
  ASA: '#2E7D32',
  PAR: '#00D592',
  OTR: '#4A5568',
};

const ZONE_LABELS: Record<string, string> = {
  ACA: 'Académico · CyT y Aulas',
  CUL: 'Cultural · Auditorios y Plazas',
  DEP: 'Deportes · Canchas y Minas',
  ASA: 'Asamblea · Espacios Abiertos',
  PAR: 'Parche · Jardín Freud y Zonas Verdes',
  OTR: 'Otro · Campus General',
};

function getCategoryMarkerIcon(category?: string | null): string {
  const color = (category && CATEGORY_COLORS[category]) || '#00D592';
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="34" height="42" viewBox="0 0 34 42">
    <path d="M17 0C7.61 0 0 7.61 0 17c0 12.75 17 25 17 25s17-12.25 17-25c0-9.39-7.61-17-17-17z" fill="${color}" stroke="#FFFFFF" stroke-width="2"/>
    <circle cx="17" cy="17" r="6.5" fill="#FFFFFF"/>
  </svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

// Subcomponente para sincronizar fitBounds y centrado al seleccionar
function MapController({
  events,
  selectedEvent,
}: {
  events: CampusEvent[];
  selectedEvent: CampusEvent | null;
}) {
  const map = useMap();

  useEffect(() => {
    if (!map || typeof google === 'undefined' || !google.maps) return;

    if (selectedEvent) {
      const lat = typeof selectedEvent.latitude === 'string' ? parseFloat(selectedEvent.latitude) : selectedEvent.latitude;
      const lng = typeof selectedEvent.longitude === 'string' ? parseFloat(selectedEvent.longitude) : selectedEvent.longitude;
      if (!isNaN(lat) && !isNaN(lng)) {
        map.panTo({ lat, lng });
        map.setZoom(17);
      }
    } else if (events.length > 0) {
      const bounds = new google.maps.LatLngBounds();
      let hasValid = false;
      events.forEach((ev) => {
        const lat = typeof ev.latitude === 'string' ? parseFloat(ev.latitude) : ev.latitude;
        const lng = typeof ev.longitude === 'string' ? parseFloat(ev.longitude) : ev.longitude;
        if (!isNaN(lat) && !isNaN(lng)) {
          bounds.extend({ lat, lng });
          hasValid = true;
        }
      });
      if (hasValid) {
        map.fitBounds(bounds, 60);
      }
    }
  }, [map, selectedEvent, events]);

  return null;
}

// Controles personalizados de navegación y centrado
function MapControls({ events }: { events: CampusEvent[] }) {
  const map = useMap();

  const fitAll = () => {
    if (!map || typeof google === 'undefined' || !google.maps || events.length === 0) return;
    const bounds = new google.maps.LatLngBounds();
    let hasValid = false;
    events.forEach((e) => {
      const lat = typeof e.latitude === 'string' ? parseFloat(e.latitude) : e.latitude;
      const lng = typeof e.longitude === 'string' ? parseFloat(e.longitude) : e.longitude;
      if (!isNaN(lat) && !isNaN(lng)) {
        bounds.extend({ lat, lng });
        hasValid = true;
      }
    });
    if (hasValid) {
      map.fitBounds(bounds, 60);
    }
  };

  return (
    <div className="absolute top-3.5 right-3.5 z-10 flex flex-col gap-2">
      <div className="flex flex-col overflow-hidden rounded-xl border border-neutral-200/90 bg-white/95 shadow-md backdrop-blur-md dark:border-neutral-800 dark:bg-[#111A24]/95">
        <button
          type="button"
          onClick={() => map?.setZoom((map.getZoom() ?? 15) + 1)}
          aria-label="Acercar mapa"
          className="flex h-8.5 w-8.5 items-center justify-center text-neutral-700 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-300 dark:hover:bg-neutral-800 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-lg">add</span>
        </button>
        <div className="h-px bg-neutral-200/80 dark:bg-neutral-800" />
        <button
          type="button"
          onClick={() => map?.setZoom((map.getZoom() ?? 15) - 1)}
          aria-label="Alejar mapa"
          className="flex h-8.5 w-8.5 items-center justify-center text-neutral-700 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-300 dark:hover:bg-neutral-800 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
        >
          <span className="material-symbols-outlined text-lg">remove</span>
        </button>
      </div>
      <button
        type="button"
        onClick={fitAll}
        aria-label="Encuadrar todos los eventos"
        title="Encuadrar todos los eventos"
        className="flex h-8.5 w-8.5 items-center justify-center rounded-xl border border-neutral-200/90 bg-white/95 text-neutral-700 shadow-md backdrop-blur-md hover:bg-neutral-100 hover:text-neutral-900 dark:border-neutral-800 dark:bg-[#111A24]/95 dark:text-neutral-300 dark:hover:bg-neutral-800 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
      >
        <span className="material-symbols-outlined text-lg">my_location</span>
      </button>
    </div>
  );
}

// Leyenda discreta de zonas del campus
function MapLegend() {
  return (
    <div className="absolute right-3.5 bottom-3.5 z-10 hidden rounded-2xl border border-neutral-200/80 bg-white/95 p-3.5 shadow-md backdrop-blur-md sm:block dark:border-neutral-800 dark:bg-[#0E1720]/95 max-w-[210px]">
      <p className="mb-2 border-b border-neutral-100 pb-1 text-[11px] font-bold uppercase tracking-wider text-neutral-500 dark:border-neutral-800 dark:text-neutral-400">
        Categorías del Campus
      </p>
      <ul className="flex flex-col gap-1.5">
        {Object.entries(ZONE_LABELS).map(([key, label]) => (
          <li key={key} className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="h-2.5 w-2.5 shrink-0 rounded-full shadow-xs"
              style={{ backgroundColor: CATEGORY_COLORS[key] || '#00D592' }}
            />
            <span className="text-[11px] text-neutral-700 dark:text-neutral-300 truncate">
              {label}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function CampusMap({
  events,
  selectedEvent,
  onSelectEvent,
}: CampusMapProps): React.JSX.Element {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  const defaultCampusCenter = { lat: 4.6383, lng: -74.084 }; // Sede Bogotá

  // Si no hay API key disponible en el entorno
  if (!apiKey) {
    return (
      <div className="flex h-full min-h-[500px] flex-col items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-200 bg-gray-50 p-6 text-center dark:border-gray-800 dark:bg-gray-900">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--color-undesparche-primary)] text-white shadow-md">
          <span className="material-symbols-outlined text-3xl" aria-hidden="true">
            map
          </span>
        </div>
        <h2 className="mt-4 font-display font-bold text-lg text-gray-900 dark:text-gray-100">
          Mapa del Campus Universitario (Sede Bogotá)
        </h2>
        <p className="mt-2 max-w-md text-sm text-gray-600 dark:text-gray-400">
          La API Key de Google Maps no está configurada (<code className="rounded bg-gray-200 px-1 py-0.5 text-xs dark:bg-gray-800">VITE_GOOGLE_MAPS_API_KEY</code>). Puedes seleccionar cualquier evento de la lista lateral para consultar su ubicación o ver el enlace en Google Maps.
        </p>

        {selectedEvent ? (
          <div className="mt-6 w-full max-w-sm rounded-[var(--radius-undesparche)] border border-blue-200 bg-blue-50/80 p-4 text-left dark:border-blue-900 dark:bg-blue-950/40">
            <p className="font-semibold text-xs text-[var(--color-undesparche-primary)] dark:text-blue-300">
              Ubicación seleccionada:
            </p>
            <p className="font-bold text-sm text-gray-900 dark:text-white line-clamp-1">
              {selectedEvent.name}
            </p>
            <p className="text-xs text-gray-600 dark:text-gray-300">
              {selectedEvent.place}
            </p>
            <p className="mt-1 font-mono text-xs tabular-nums text-gray-500">
              Coordenadas: {selectedEvent.latitude}, {selectedEvent.longitude}
            </p>
            <a
              href={`https://www.google.com/maps?q=${selectedEvent.latitude},${selectedEvent.longitude}`}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-3 inline-flex items-center gap-1.5 font-semibold text-xs text-[var(--color-undesparche-primary)] hover:underline dark:text-blue-400"
            >
              <span className="material-symbols-outlined text-sm" aria-hidden="true">
                open_in_new
              </span>
              Abrir en Google Maps
            </a>
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div className="relative h-full min-h-[500px] w-full overflow-hidden rounded-[var(--radius-undesparche)] border border-neutral-200/80 shadow-xs dark:border-neutral-800">
      <APIProvider apiKey={apiKey}>
        <Map
          style={{ width: '100%', height: '100%' }}
          defaultCenter={defaultCampusCenter}
          defaultZoom={15}
          gestureHandling="greedy"
          disableDefaultUI={true}
        >
          <MapController events={events} selectedEvent={selectedEvent} />
          <MapControls events={events} />
          <MapLegend />

          {events.map((ev) => {
            const lat = typeof ev.latitude === 'string' ? parseFloat(ev.latitude) : ev.latitude;
            const lng = typeof ev.longitude === 'string' ? parseFloat(ev.longitude) : ev.longitude;
            if (isNaN(lat) || isNaN(lng)) return null;

            return (
              <Marker
                key={ev.id}
                position={{ lat, lng }}
                title={ev.name}
                icon={getCategoryMarkerIcon(ev.category)}
                onClick={() => onSelectEvent(ev)}
              />
            );
          })}

          {selectedEvent && (
            <InfoWindow
              position={{
                lat: typeof selectedEvent.latitude === 'string' ? parseFloat(selectedEvent.latitude) : selectedEvent.latitude,
                lng: typeof selectedEvent.longitude === 'string' ? parseFloat(selectedEvent.longitude) : selectedEvent.longitude,
              }}
              onCloseClick={() => onSelectEvent(null)}
            >
              <div className="p-1 max-w-[250px] text-neutral-900 dark:text-neutral-100 font-sans">
                <span
                  className="rounded-full px-2 py-0.5 font-semibold text-[10px] text-white"
                  style={{ backgroundColor: CATEGORY_COLORS[selectedEvent.category || ''] || '#00D592' }}
                >
                  {selectedEvent.category ? EVENT_CATEGORY_LABELS[selectedEvent.category] : 'Campus'}
                </span>
                <h3 className="mt-1.5 font-bold text-sm leading-snug line-clamp-2">
                  {selectedEvent.name}
                </h3>
                <p className="mt-1 text-xs text-neutral-600 dark:text-neutral-300 line-clamp-1 flex items-center gap-1">
                  <span className="material-symbols-outlined text-xs">location_on</span>
                  {selectedEvent.place}
                </p>
                <div className="mt-3 flex items-center justify-between border-t border-neutral-100 dark:border-neutral-800 pt-2 gap-2 text-xs font-semibold">
                  <Link
                    to={`/events/${selectedEvent.id}`}
                    className="text-[#00875A] dark:text-[#00F5A0] hover:underline"
                  >
                    Ver Detalles
                  </Link>
                  <a
                    href={`https://www.google.com/maps?q=${selectedEvent.latitude},${selectedEvent.longitude}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-neutral-500 hover:text-neutral-800 dark:hover:text-neutral-200 flex items-center gap-0.5"
                  >
                    Cómo llegar
                    <span className="material-symbols-outlined text-xs">open_in_new</span>
                  </a>
                </div>
              </div>
            </InfoWindow>
          )}
        </Map>
      </APIProvider>
    </div>
  );
}
