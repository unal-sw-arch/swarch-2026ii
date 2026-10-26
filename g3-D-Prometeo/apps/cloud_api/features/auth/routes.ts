import { randomBytes } from 'node:crypto';
import { Buffer } from 'node:buffer';
import { Hono } from 'hono';
import { hashPassword, verifyPassword } from '../../domain/security/password';
import type { CloudEnvironment } from '../../types';
import { bearerToken, findSession, tokenHash } from './session';

const SESSION_DURATION_MS = 12 * 60 * 60 * 1000;
const routes = new Hono<CloudEnvironment>();

routes.post('/register', async (c) => {
  const existing = await c.env.DB.prepare(
    "SELECT 1 FROM users WHERE role = 'admin' LIMIT 1",
  ).first();
  if (existing) return c.json({ error: 'ADMIN_ALREADY_EXISTS' }, 409);
  let body: unknown;
  try { body = await c.req.json<unknown>(); } catch {
    return c.json({ error: 'INVALID_JSON' }, 400);
  }
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return c.json({ error: 'VALIDATION_ERROR' }, 422);
  }
  const data = body as Record<string, unknown>;
  const fullName = typeof data.fullName === 'string' ? data.fullName.trim() : '';
  const username = typeof data.username === 'string'
    ? data.username.trim().toLowerCase() : '';
  const password = typeof data.password === 'string' ? data.password : '';
  if (fullName.length < 2 || fullName.length > 100 ||
      !/^[a-z0-9](?:[a-z0-9._-]{1,30}[a-z0-9])?$/.test(username) ||
      password.length < 12 || password.length > 128) {
    return c.json({ error: 'VALIDATION_ERROR' }, 422);
  }
  const id = await c.env.DB.prepare(
    `INSERT INTO users (name, role, username, password_hash)
     SELECT ?, 'admin', ?, ?
     WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'admin')
     RETURNING id`,
  ).bind(fullName, username, await hashPassword(password)).first<{ id: number }>();
  if (!id) return c.json({ error: 'ADMIN_ALREADY_EXISTS' }, 409);
  return c.json({ admin: { id: Number(id?.id), fullName, username, role: 'admin' } }, 201);
});

routes.post('/login', async (c) => {
  let body: unknown;
  try {
    body = await c.req.json<unknown>();
  } catch {
    return c.json({ error: 'INVALID_JSON' }, 400);
  }
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return c.json({ error: 'INVALID_CREDENTIALS' }, 401);
  }
  const data = body as Record<string, unknown>;
  const username = typeof data.username === 'string'
    ? data.username.trim().toLowerCase()
    : '';
  const password = typeof data.password === 'string' ? data.password : '';
  if (!username || !password) {
    return c.json({ error: 'INVALID_CREDENTIALS' }, 401);
  }

  const user = await c.env.DB.prepare(
    `SELECT id, name, username, role, password_hash AS passwordHash
     FROM users WHERE username = ? LIMIT 1`,
  ).bind(username).first<{
    id: number;
    name: string;
    username: string;
    role: string;
    passwordHash: string;
  }>();
  if (!user || !(await verifyPassword(password, user.passwordHash))) {
    return c.json({
      error: 'INVALID_CREDENTIALS',
      message: 'Usuario o contraseña incorrectos.',
    }, 401);
  }

  const token = Buffer.from(randomBytes(32)).toString('base64url');
  const now = new Date();
  const expiresAt = new Date(now.getTime() + SESSION_DURATION_MS);
  await c.env.DB.batch([
    c.env.DB.prepare(
      'DELETE FROM cloud_sessions WHERE expires_at <= ? OR revoked_at IS NOT NULL',
    ).bind(now.toISOString()),
    c.env.DB.prepare(
      `INSERT INTO cloud_sessions
       (user_id, token_hash, created_at, expires_at)
       VALUES (?, ?, ?, ?)`,
    ).bind(user.id, tokenHash(token), now.toISOString(), expiresAt.toISOString()),
  ]);

  return c.json({
    token,
    expiresAt: expiresAt.toISOString(),
    user: {
      id: user.id,
      fullName: user.name,
      username: user.username,
      role: user.role,
    },
  });
});

routes.get('/session', async (c) => {
  const session = await findSession(c.env.DB, c.req.header('authorization'));
  if (!session) return c.json({ error: 'INVALID_SESSION' }, 401);
  return c.json({ expiresAt: session.expiresAt, user: session.user });
});

routes.post('/logout', async (c) => {
  const token = bearerToken(c.req.header('authorization'));
  if (token) {
    await c.env.DB.prepare(
      `UPDATE cloud_sessions SET revoked_at = ?
       WHERE token_hash = ? AND revoked_at IS NULL`,
    ).bind(new Date().toISOString(), tokenHash(token)).run();
  }
  return c.body(null, 204);
});

export default routes;
