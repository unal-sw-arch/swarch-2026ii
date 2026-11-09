import { getAccessToken } from './auth';

const GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Cliente del API Gateway para componentes de servidor y route handlers.
 * Lee el access token de la cookie httpOnly y nunca lo expone al navegador.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('accept', 'application/json');
  if (init.body && !headers.has('content-type')) {
    headers.set('content-type', 'application/json');
  }

  const token = getAccessToken();
  if (token) {
    headers.set('authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${GATEWAY_URL}${path}`, {
    ...init,
    headers,
    cache: 'no-store',
  });

  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }

  if (!response.ok) {
    const error = (body ?? {}) as { code?: string; message?: string; details?: unknown };
    throw new ApiError(
      response.status,
      error.code ?? 'INTERNAL_ERROR',
      error.message ?? 'El API Gateway respondió con un error',
      error.details,
    );
  }

  return body as T;
}
