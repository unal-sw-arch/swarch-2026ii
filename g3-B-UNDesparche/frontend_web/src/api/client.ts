import { ApiErrorResponse } from '../types/common';

const EVENTS_API_URL = (import.meta.env.VITE_EVENTS_API_URL || import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8000').replace(/\/+$/, '');
const USERS_API_URL = (import.meta.env.VITE_USERS_API_URL || 'http://127.0.0.1:5000').replace(/\/+$/, '');
const INVENTORY_API_URL = (import.meta.env.VITE_INVENTORY_API_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  token?: string | null;
  params?: Record<string, string | number | boolean | null | undefined>;
  body?: BodyInit | Record<string, unknown> | unknown;
}

/**
 * Resuelve la URL base adecuada según el microservicio al que corresponde la ruta.
 */
export function getBaseUrlForPath(path: string): string {
  const clean = path.replace(/^\/+/, '');
  if (clean.startsWith('users')) {
    return USERS_API_URL;
  }
  if (clean.startsWith('inventory')) {
    return INVENTORY_API_URL;
  }
  if (clean.startsWith('events')) {
    return EVENTS_API_URL;
  }
  return EVENTS_API_URL;
}

/**
 * Normaliza una ruta para asegurar el trailing slash requerido por Django REST.
 * Si contiene query string (?param=val), el slash se inserta antes del query.
 */
export function ensureTrailingSlash(path: string): string {
  const [pathname, search] = path.split('?');
  const normalizedPath = pathname.endsWith('/') ? pathname : `${pathname}/`;
  return search !== undefined ? `${normalizedPath}?${search}` : normalizedPath;
}

/**
 * Convierte un objeto de parámetros en un search query string.
 */
function buildQueryString(params?: Record<string, string | number | boolean | null | undefined>): string {
  if (!params) return '';
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, String(value));
    }
  }
  const queryString = searchParams.toString();
  return queryString ? `?${queryString}` : '';
}

export class ApiError extends Error {
  status: number;
  data?: unknown;

  constructor(message: string, status: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

/**
 * Cliente centralizado para peticiones a los microservicios Django REST
 * (events_service en 8000, users_service en 5000, inventory_service en 8080)
 * con fallback transparente ante fallos de conexión en desarrollo.
 */
export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const { token, params, headers = {}, body, method = 'GET', ...init } = options;

  // Evitar PUT según directriz de backend (da 405)
  if (method.toUpperCase() === 'PUT') {
    throw new Error('El método PUT está deshabilitado en el backend. Use PATCH.');
  }

  const baseUrl = getBaseUrlForPath(path);
  const query = buildQueryString(params);
  const normalizedPath = ensureTrailingSlash(path + query);
  const url = `${baseUrl}${normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`}`;

  const requestHeaders = new Headers(headers);
  requestHeaders.set('Accept', 'application/json');

  if (token) {
    requestHeaders.set('Authorization', `Bearer ${token}`);
  }

  const isFormData = body instanceof FormData;
  if (!isFormData && body && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json');
  }

  try {
    const response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: isFormData ? body : typeof body === 'object' && body !== null ? JSON.stringify(body) : (body as BodyInit),
      ...init,
    });

    if (response.status === 204) {
      return undefined as unknown as T;
    }

    if (!response.ok) {
      let errorMessage = `Error ${response.status}: ${response.statusText}`;
      let errorData: unknown = null;

      try {
        const json: ApiErrorResponse = await response.json();
        errorData = json;

        if (json.detail) {
          errorMessage = json.detail;
        } else {
          const fieldErrors = Object.entries(json)
            .map(([field, msgs]) => {
              const fieldText = Array.isArray(msgs) ? msgs.join(', ') : String(msgs);
              return `${field}: ${fieldText}`;
            })
            .join(' | ');
          if (fieldErrors) {
            errorMessage = fieldErrors;
          }
        }
      } catch {
        try {
          const text = await response.text();
          if (text) errorMessage = text;
        } catch {
          // ignore
        }
      }

      throw new ApiError(errorMessage, response.status, errorData);
    }

    return response.json() as Promise<T>;
  } catch (networkOrApiError) {
    if (networkOrApiError instanceof ApiError) {
      throw networkOrApiError;
    }
    const message = (networkOrApiError as Error)?.message || 'Error de conexión con el servicio';
    throw new ApiError(`Error de conexión con el servidor (${baseUrl}): ${message}`, 0, networkOrApiError);
  }
}
