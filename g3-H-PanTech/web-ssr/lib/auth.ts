import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { ACCESS_COOKIE, REFRESH_COOKIE } from './cookies';
import { decodeSession, isExpired, isStaff, type Session } from './session';

export function getAccessToken(): string | undefined {
  return cookies().get(ACCESS_COOKIE)?.value;
}

export function getRefreshToken(): string | undefined {
  return cookies().get(REFRESH_COOKIE)?.value;
}

export function getSession(): Session | null {
  const session = decodeSession(getAccessToken());
  if (!session || isExpired(session)) return null;
  return session;
}

export function requireSession(): Session {
  const session = getSession();
  if (!session) redirect('/login');
  return session;
}

export function requireStaff(): Session {
  const session = requireSession();
  if (!isStaff(session)) redirect('/forbidden');
  return session;
}

export function requirePermission(permission: string): Session {
  const session = requireSession();
  if (!session.permissions.includes(permission)) redirect('/forbidden');
  return session;
}
