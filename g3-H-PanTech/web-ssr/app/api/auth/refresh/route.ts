import { NextRequest, NextResponse } from 'next/server';
import { REFRESH_COOKIE, clearAuthCookies, setAuthCookies } from '@/lib/cookies';
import type { AuthResponse } from '@/lib/types';

const GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8080';

export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  if (!refreshToken) {
    const response = NextResponse.json(
      { code: 'UNAUTHENTICATED', message: 'No hay una sesión que renovar', details: {} },
      { status: 401 },
    );
    clearAuthCookies(response);
    return response;
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${GATEWAY_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
      cache: 'no-store',
    });
  } catch {
    return NextResponse.json(
      { code: 'INTERNAL_ERROR', message: 'No se pudo contactar al API Gateway', details: {} },
      { status: 502 },
    );
  }

  const text = await upstream.text();

  if (!upstream.ok) {
    const response = new NextResponse(
      text || JSON.stringify({ code: 'UNAUTHENTICATED', message: 'La sesión expiró' }),
      {
        status: upstream.status,
        headers: { 'content-type': 'application/json' },
      },
    );
    clearAuthCookies(response);
    return response;
  }

  let tokens: AuthResponse;
  try {
    tokens = JSON.parse(text) as AuthResponse;
  } catch {
    return NextResponse.json(
      { code: 'INTERNAL_ERROR', message: 'Respuesta inválida del API Gateway', details: {} },
      { status: 502 },
    );
  }

  const response = NextResponse.json({ user: tokens.user });
  setAuthCookies(response, tokens);
  return response;
}
