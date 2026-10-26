import React from 'react';
import { APIProvider, Map, Marker } from '@vis.gl/react-google-maps';

export interface EventMapProps {
  lat: number | string;
  lng: number | string;
  height?: string;
  zoom?: number;
  placeName?: string;
}

export function EventMap({
  lat,
  lng,
  height = '240px',
  zoom = 16,
  placeName,
}: EventMapProps): React.JSX.Element {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

  const parsedLat = typeof lat === 'string' ? parseFloat(lat) : lat;
  const parsedLng = typeof lng === 'string' ? parseFloat(lng) : lng;

  const isValidCoords =
    !isNaN(parsedLat) &&
    !isNaN(parsedLng) &&
    parsedLat >= -90 &&
    parsedLat <= 90 &&
    parsedLng >= -180 &&
    parsedLng <= 180;

  if (!isValidCoords) {
    return (
      <div
        style={{ height }}
        className="flex flex-col items-center justify-center rounded-[var(--radius-undesparche)] border border-gray-200 bg-gray-100 p-4 text-center dark:border-gray-800 dark:bg-gray-800/40"
      >
        <span className="material-symbols-outlined text-3xl text-gray-400" aria-hidden="true">
          location_off
        </span>
        <p className="mt-2 text-sm text-gray-500">Ubicación no disponible</p>
      </div>
    );
  }

  const mapsUrl = `https://www.google.com/maps?q=${parsedLat},${parsedLng}`;

  // Si no hay API key disponible, mostramos un fallback elegante y accesible con coordenadas tabular-nums
  if (!apiKey) {
    return (
      <div
        style={{ height }}
        className="relative flex flex-col items-center justify-between overflow-hidden rounded-[var(--radius-undesparche)] border border-gray-200 bg-linear-to-b from-blue-50/50 to-emerald-50/50 p-4 text-center dark:border-gray-800 dark:from-gray-900 dark:to-gray-850"
      >
        <div className="flex flex-col items-center pt-2">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-undesparche-primary)] text-white shadow-sm">
            <span className="material-symbols-outlined text-2xl" aria-hidden="true">
              pin_drop
            </span>
          </div>
          {placeName && (
            <p className="mt-2 font-semibold text-sm text-gray-800 dark:text-gray-200 line-clamp-1">
              {placeName}
            </p>
          )}
          <p className="mt-1 font-mono text-xs tabular-nums text-gray-500">
            {parsedLat.toFixed(6)}, {parsedLng.toFixed(6)}
          </p>
        </div>

        <a
          href={mapsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 rounded-[var(--radius-undesparche)] bg-white px-3 py-1.5 font-medium text-xs text-[var(--color-undesparche-primary)] shadow-xs ring-1 ring-gray-300 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-[var(--color-undesparche-lime)] dark:bg-gray-800 dark:text-white dark:ring-gray-700"
        >
          <span className="material-symbols-outlined text-sm" aria-hidden="true">
            open_in_new
          </span>
          Abrir en Google Maps
        </a>
      </div>
    );
  }

  return (
    <div
      style={{ height }}
      className="relative overflow-hidden rounded-[var(--radius-undesparche)] border border-gray-200 shadow-xs dark:border-gray-800"
    >
      <APIProvider apiKey={apiKey}>
        <Map
          style={{ width: '100%', height: '100%' }}
          defaultCenter={{ lat: parsedLat, lng: parsedLng }}
          defaultZoom={zoom}
          gestureHandling="greedy"
          disableDefaultUI={false}
        >
          <Marker position={{ lat: parsedLat, lng: parsedLng }} />
        </Map>
      </APIProvider>
    </div>
  );
}
