import { NextRequest, NextResponse } from 'next/server';
import { ACCESS_COOKIE, REFRESH_COOKIE, clearAuthCookies } from '@/lib/cookies';

const GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8080';

export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  // El logout local siempre debe funcionar aunque el Gateway o Identity fallen.
  try {
    const headers: Record<string, string> = {
      'content-type': 'application/json',
      accept: 'application/json',
    };
    if (accessToken) {
      headers.authorization = `Bearer ${accessToken}`;
    }

    await fetch(`${GATEWAY_URL}/auth/logout`, {
      method: 'POST',
      headers,
      body: JSON.stringify(refreshToken ? { refresh_token: refreshToken } : {}),
      cache: 'no-store',
    });
  } catch {
    // Se ignoran los errores del upstream: la cookie se limpia igualmente.
  }

  const response = NextResponse.json({ message: 'Sesión cerrada' });
  clearAuthCookies(response);
  return response;
}
