import { createHash } from 'node:crypto';
import type { MiddlewareHandler } from 'hono';
import type { AuthenticatedSession, CloudEnvironment } from '../../types';

type SessionRow = {
  sessionId: number;
  expiresAt: string;
  userId: number;
  fullName: string;
  username: string;
  role: string;
};

export function tokenHash(token: string): string {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}

export function bearerToken(authorization?: string): string | undefined {
  return authorization?.match(/^Bearer ([A-Za-z0-9_-]{32,})$/)?.[1];
}

export async function findSession(
  database: D1Database,
  authorization?: string,
): Promise<AuthenticatedSession | undefined> {
  const token = bearerToken(authorization);
  if (!token) return undefined;
  const row = await database.prepare(
    `SELECT s.id AS sessionId, s.expires_at AS expiresAt,
            u.id AS userId, u.name AS fullName, u.username, u.role
     FROM cloud_sessions s
     JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?
     LIMIT 1`,
  ).bind(tokenHash(token), new Date().toISOString()).first<SessionRow>();
  if (!row) return undefined;
  return {
    sessionId: row.sessionId,
    expiresAt: row.expiresAt,
    user: {
      id: row.userId,
      fullName: row.fullName,
      username: row.username,
      role: row.role,
    },
  };
}

export const requireSession: MiddlewareHandler<CloudEnvironment> = async (c, next) => {
  const session = await findSession(c.env.DB, c.req.header('authorization'));
  if (!session) return c.json({ error: 'INVALID_SESSION' }, 401);
  c.set('session', session);
  await next();
};

export const requireAdmin: MiddlewareHandler<CloudEnvironment> = async (c, next) => {
  if (c.get('session').user.role !== 'admin') {
    return c.json({ error: 'ADMIN_REQUIRED' }, 403);
  }
  await next();
};

export async function canAccessHall(
  database: D1Database,
  session: AuthenticatedSession,
  hallId: number,
): Promise<boolean> {
  if (session.user.role === 'admin') {
    return (await database.prepare(
      'SELECT 1 FROM hall WHERE id = ? LIMIT 1',
    ).bind(hallId).first()) !== null;
  }
  return (await database.prepare(
    `SELECT 1 FROM employee_halls
     WHERE user_id = ? AND hall_id = ? LIMIT 1`,
  ).bind(session.user.id, hallId).first()) !== null;
}
