import { Hono } from 'hono';
import { cors } from 'hono/cors';
import authRoutes from './features/auth/routes';
import { findSession } from './features/auth/session';
import { RestaurantCoordinator } from './infrastructure/realtime/restaurant_coordinator';
import catalogWriteRoutes from './features/catalog/routes';
import orderRoutes from './features/orders/routes';
import readOnlyRoutes from './features/read_model/routes';
import liveLayoutRoutes from './features/halls/routes';
import adminOperationRoutes from './features/admin/routes';
import type { CloudEnvironment } from './types';

const app = new Hono<CloudEnvironment>();
const coordinatorName = 'restaurant:default';

// Default browser origins for the containerized development clients
// (web-operational on :8080, web-admin on :8081). Override in any environment
// by setting the ALLOWED_ORIGINS var (comma-separated list).
const defaultAllowedOrigins = [
  'http://localhost:8080',
  'http://localhost:8081',
];

app.use('*', (c, next) => {
  const configured = c.env.ALLOWED_ORIGINS
    ?.split(',')
    .map((origin) => origin.trim())
    .filter((origin) => origin.length > 0);
  const allowedOrigins = configured && configured.length > 0
    ? configured
    : defaultAllowedOrigins;
  return cors({
    origin: (origin) => (allowedOrigins.includes(origin) ? origin : null),
    allowMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization'],
    credentials: true,
  })(c, next);
});

app.use('/api/v1/*', async (c, next) => {
  await next();
  c.header('Cache-Control', 'no-store');
});

app.use('/api/v1/auth/logout', async (c, next) => {
  const session = await findSession(c.env.DB, c.req.header('authorization'));
  await next();
  if (session && c.res.status >= 200 && c.res.status < 300) {
    await c.env.RESTAURANT_COORDINATOR.getByName(coordinatorName).fetch(
      new Request(`https://coordinator/__session/${session.sessionId}`, {
        method: 'DELETE',
      }),
    );
  }
});

app.use('/api/v1/*', async (c, next) => {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(c.req.method) ||
      new URL(c.req.url).pathname.startsWith('/api/v1/auth/')) {
    return next();
  }
  const session = await findSession(c.env.DB, c.req.header('authorization'));
  if (!session) return c.json({ error: 'INVALID_SESSION' }, 401);
  const headers = new Headers(c.req.raw.headers);
  headers.set('x-realtime-session-id', String(session.sessionId));
  headers.set('x-realtime-user-id', String(session.user.id));
  headers.set('x-realtime-role', session.user.role);
  headers.set('x-realtime-expires-at', session.expiresAt);
  const body = await c.req.raw.arrayBuffer();
  const request = new Request(c.req.url, {
    method: c.req.method,
    headers,
    body: body.byteLength === 0 ? undefined : body,
  });
  return c.env.RESTAURANT_COORDINATOR.getByName(coordinatorName).fetch(request);
});

app.get('/', (c) => c.json({
  service: 'restauranteapp-cloud',
  status: 'running',
}));

app.get('/health', async (c) => {
  try {
    const result = await c.env.DB.prepare('SELECT 1 AS value').first<{
      value: number;
    }>();

    if (result?.value !== 1) {
      throw new Error('D1 returned an unexpected health-check result.');
    }

    return c.json({
      status: 'ok',
      database: 'connected',
      environment: c.env.ENVIRONMENT ?? 'unknown',
    });
  } catch (error: unknown) {
    console.error('D1 health check failed', error);
    return c.json({
      status: 'error',
      database: 'unavailable',
      environment: c.env.ENVIRONMENT ?? 'unknown',
    }, 503);
  }
});

app.get('/api/v1/realtime', async (c) => {
  if (c.req.header('upgrade')?.toLowerCase() !== 'websocket') {
    return c.json({ error: 'WEBSOCKET_REQUIRED' }, 426);
  }
  // Browsers cannot set custom headers on the WebSocket handshake, so web
  // clients authenticate with an `access_token` query parameter. Native
  // clients may still use the Authorization header.
  const queryToken = new URL(c.req.url).searchParams.get('access_token');
  const authorization = c.req.header('authorization')
    ?? (queryToken ? `Bearer ${queryToken}` : undefined);
  const session = await findSession(c.env.DB, authorization);
  if (!session) return c.json({ error: 'INVALID_SESSION' }, 401);
  const headers = new Headers(c.req.raw.headers);
  headers.set('x-realtime-session-id', String(session.sessionId));
  headers.set('x-realtime-user-id', String(session.user.id));
  headers.set('x-realtime-role', session.user.role);
  headers.set('x-realtime-expires-at', session.expiresAt);
  return c.env.RESTAURANT_COORDINATOR.getByName(coordinatorName).fetch(
    new Request(c.req.raw, { headers }),
  );
});

app.route('/api/v1/auth', authRoutes);
app.route('/api/v1/admin', catalogWriteRoutes);
app.route('/api/v1/admin', adminOperationRoutes);
app.route('/api/v1', orderRoutes);
app.route('/api/v1', readOnlyRoutes);
app.route('/api/v1', liveLayoutRoutes);

app.onError((error, c) => {
  console.error('Unhandled cloud API error:', error);
  return c.json({ error: 'INTERNAL_ERROR' }, 500);
});

app.notFound((c) => c.json({ error: 'NOT_FOUND' }, 404));

export default app;
export { RestaurantCoordinator };
