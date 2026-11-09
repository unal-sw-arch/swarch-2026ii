import { NextRequest, NextResponse } from 'next/server';
import { setAuthCookies } from '@/lib/cookies';
import type { AuthResponse } from '@/lib/types';

const GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8080';

export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  let payload: unknown;
  try {
    payload = await request.json();
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'El cuerpo de la petición no es válido', details: {} },
      { status: 400 },
    );
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${GATEWAY_URL}/auth/login`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify(payload),
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
    return new NextResponse(text || JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Error de autenticación' }), {
      status: upstream.status,
      headers: { 'content-type': 'application/json' },
    });
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
