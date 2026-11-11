import type { NextResponse } from 'next/server';
import type { AuthTokens } from './types';

export const ACCESS_COOKIE = process.env.JWT_COOKIE_NAME ?? 'panaderia_access';
export const REFRESH_COOKIE = process.env.JWT_REFRESH_COOKIE_NAME ?? 'panaderia_refresh';

function options(maxAge: number) {
  return {
    httpOnly: true,
    sameSite: 'lax' as const,
    secure: process.env.COOKIE_SECURE === 'true',
    path: '/',
    maxAge,
  };
}

export function setAuthCookies(response: NextResponse, tokens: AuthTokens): void {
  response.cookies.set(ACCESS_COOKIE, tokens.access_token, options(tokens.expires_in));
  response.cookies.set(REFRESH_COOKIE, tokens.refresh_token, options(tokens.refresh_expires_in));
}

export function clearAuthCookies(response: NextResponse): void {
  response.cookies.set(ACCESS_COOKIE, '', options(0));
  response.cookies.set(REFRESH_COOKIE, '', options(0));
}
