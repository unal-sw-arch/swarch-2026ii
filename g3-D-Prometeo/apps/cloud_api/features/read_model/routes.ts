import { Hono, type Context } from 'hono';
import { canAccessHall, requireAdmin, requireSession } from '../auth/session';
import { readCachedCatalog } from '../catalog/cache';
import { readHalls, readLayout } from './repository';
import type { CloudEnvironment } from '../../types';

const routes = new Hono<CloudEnvironment>();

routes.use('*', requireSession);
routes.use('/catalog', requireAdmin);
routes.use('/menus', requireAdmin);
routes.use('/menus/*', requireAdmin);

routes.get('/bootstrap', async (c) => {
  const session = c.get('session');
  const isAdmin = session.user.role === 'admin';
  const [cachedCatalog, halls] = await Promise.all([
    isAdmin ? readCachedCatalog(c.env.DB, c.req.url) : Promise.resolve(null),
    readHalls(c.env.DB, isAdmin ? undefined : session.user.id),
  ]);
  if (cachedCatalog) catalogHeaders(c, cachedCatalog.status, cachedCatalog.version);
  return c.json({
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    catalog: cachedCatalog?.catalog ?? null,
    halls,
    user: session.user,
  });
});

routes.get('/catalog', async (c) => {
  const cached = await readCachedCatalog(c.env.DB, c.req.url);
  catalogHeaders(c, cached.status, cached.version);
  return c.json(cached.catalog);
});

routes.get('/menus', async (c) => {
  const cached = await readCachedCatalog(c.env.DB, c.req.url);
  catalogHeaders(c, cached.status, cached.version);
  const { menus } = cached.catalog;
  return c.json({ menus });
});

routes.get('/menus/:menuId', async (c) => {
  const menuId = positiveId(c.req.param('menuId'));
  if (!menuId) return c.json({ error: 'MENU_NOT_FOUND' }, 404);
  const cached = await readCachedCatalog(c.env.DB, c.req.url);
  catalogHeaders(c, cached.status, cached.version);
  const menu = cached.catalog.menus.find((candidate) => candidate.id === menuId);
  if (!menu) return c.json({ error: 'MENU_NOT_FOUND' }, 404);
  return c.json({ menu });
});

routes.get('/halls', async (c) => {
  const session = c.get('session');
  return c.json({
    halls: await readHalls(
      c.env.DB,
      session.user.role === 'admin' ? undefined : session.user.id,
    ),
  });
});

routes.get('/halls/:hallId/layout', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId) return c.json({ error: 'HALL_NOT_FOUND' }, 404);
  const session = c.get('session');
  if (session.user.role !== 'admin' &&
      !await canAccessHall(c.env.DB, session, hallId)) {
    return c.json({ error: 'HALL_NOT_ASSIGNED' }, 403);
  }
  const layout = await readLayout(c.env.DB, hallId);
  if (!layout) return c.json({ error: 'HALL_NOT_FOUND' }, 404);
  return c.json(layout);
});

function positiveId(value: string): number | undefined {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

function catalogHeaders(c: Context<CloudEnvironment>, status: string, version: number) {
  c.header('X-Catalog-Cache', status);
  c.header('X-Catalog-Version', String(version));
}

export default routes;
