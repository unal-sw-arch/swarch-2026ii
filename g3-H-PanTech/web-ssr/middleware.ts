import { NextRequest, NextResponse } from 'next/server';
import { ACCESS_COOKIE, REFRESH_COOKIE, clearAuthCookies, setAuthCookies } from './lib/cookies';
import { canAccess, decodeSession, homeFor, isExpired, type Session } from './lib/session';
import type { AuthResponse } from './lib/types';

const GATEWAY_URL = process.env.API_GATEWAY_URL ?? 'http://localhost:8080';
const PUBLIC_PATHS = new Set(['/', '/menu', '/login', '/register', '/forbidden', '/health']);

export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (PUBLIC_PATHS.has(pathname)) {
    if (pathname === '/login' || pathname === '/register') {
      const session = decodeSession(request.cookies.get(ACCESS_COOKIE)?.value);
      if (session && !isExpired(session)) {
        return NextResponse.redirect(new URL(homeFor(session), request.url));
      }
    }
    return NextResponse.next();
  }

  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  let session = decodeSession(accessToken);

  if (!session || isExpired(session)) {
    const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
    const refreshed = refreshToken ? await refreshTokens(refreshToken) : null;

    if (!refreshed) {
      return redirectToLogin(request, pathname + search);
    }

    session = decodeSession(refreshed.access_token);
    const response = guard(request, session);
    setAuthCookies(response, refreshed);
    return response;
  }

  return guard(request, session);
}

function guard(request: NextRequest, session: Session | null): NextResponse {
  if (!session || !canAccess(session, request.nextUrl.pathname)) {
    return NextResponse.rewrite(new URL('/forbidden', request.url), { status: 403 });
  }
  return NextResponse.next();
}

function redirectToLogin(request: NextRequest, next: string): NextResponse {
  const login = new URL('/login', request.url);
  login.searchParams.set('next', next);
  const response = NextResponse.redirect(login);
  clearAuthCookies(response);
  return response;
}

async function refreshTokens(refreshToken: string): Promise<AuthResponse | null> {
  try {
    const response = await fetch(`${GATEWAY_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
      cache: 'no-store',
    });
    if (!response.ok) return null;
    return (await response.json()) as AuthResponse;
  } catch {
    return null;
  }
}

export const config = {
  matcher: ['/((?!api/|_next/static|_next/image|favicon.ico|health).*)'],
};
