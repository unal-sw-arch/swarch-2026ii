import { rows } from '../../shared/http';

export type RealtimePatch = {
  type: string;
  resource: string;
  version: number;
  mutationId: string;
  occurredAt: string;
  hallId?: number;
  payload: Record<string, unknown>;
  audienceHallIds?: number[];
  audienceUserIds?: number[];
  adminOnly?: boolean;
};

export async function patchesForMutation(database: D1Database, request: Request,
  responseBody: unknown, mutationId: string, startedAt: string): Promise<RealtimePatch[]> {
  const path = new URL(request.url).pathname;
  const data = isObject(responseBody) ? responseBody : {};
  const occurredAt = new Date().toISOString();
  const hallMatch = path.match(/^\/api\/v1\/(?:admin\/)?halls\/(\d+)/);
  const hallId = hallMatch ? Number(hallMatch[1]) : undefined;
  const patches: RealtimePatch[] = [];

  if (hallId !== undefined && path.endsWith('/live-layout')) {
    patches.push(await patch(database, {
      type: 'hall.layout.changed', resource: `hall:${hallId}:layout`, mutationId,
      occurredAt, hallId, payload: { operation: 'replace', layout: data },
    }));
    return patches;
  }

  if (hallId !== undefined && path.endsWith('/layout')) {
    patches.push(await patch(database, {
      type: 'hall.layout.changed', resource: `hall:${hallId}:layout`, mutationId,
      occurredAt, hallId, payload: { operation: 'replace', layout: data },
    }));
    return patches;
  }

  if (hallId !== undefined &&
      (path.includes('/orders') || path.endsWith('/external-orders'))) {
    const bulkDelete = request.method === 'DELETE' &&
      /^\/api\/v1\/admin\/halls\/\d+\/orders$/.test(path);
    const individualDelete = request.method === 'DELETE' &&
      /^\/api\/v1\/admin\/halls\/\d+\/orders\/\d+$/.test(path);
    const orderId = numberFromPath(path, /\/orders\/(\d+)/) ??
      (isObject(data.order) ? Number(data.order.id) : undefined);
    const deleted = data.deleted === true;
    patches.push(await patch(database, {
      type: 'hall.orders.changed', resource: `hall:${hallId}:orders`, mutationId,
      occurredAt, hallId,
      payload: bulkDelete
        ? { operation: 'delete-many', statuses: data.statuses,
          deletedCount: data.deletedCount }
        : deleted ? { operation: 'delete', orderId } :
        { operation: 'upsert', order: data.order },
    }));
    if (((path.endsWith('/status') || path.endsWith('/pay')) &&
          isObject(data.order) && data.order.status === 'closed') ||
        (individualDelete && data.status === 'closed') ||
        (bulkDelete && Array.isArray(data.statuses) && data.statuses.includes('closed'))) {
      patches.push(await patch(database, {
        type: 'dashboard.changed', resource: 'dashboard', mutationId, occurredAt,
        payload: bulkDelete
          ? { operation: 'orders-deleted', hallId, deletedCount: data.deletedCount }
          : { operation: 'order-billed', order: data.order },
        adminOnly: true,
      }));
    }
    const activity = await database.prepare(
      `SELECT a.id, a.author_id AS authorId, u.name AS author, a.hall_id AS hallId,
              a.type, a.modification, a.created_at AS createdAt
       FROM activity_log a LEFT JOIN users u ON u.id = a.author_id
       WHERE a.created_at >= ? ORDER BY a.created_at DESC LIMIT 1`,
    ).bind(startedAt).first<Record<string, unknown>>();
    if (activity) {
      patches.push(await patch(database, {
        type: 'activity.created', resource: 'activity', mutationId, occurredAt,
        payload: { activity }, adminOnly: true,
      }));
    }
    return patches;
  }

  const catalogEvent = await catalogPatch(database, request, data, mutationId, occurredAt);
  if (catalogEvent) patches.push(catalogEvent);
  if (path === '/api/v1/admin/halls' && request.method === 'POST') {
    patches.push(await patch(database, {
      type: 'halls.changed', resource: 'halls', mutationId, occurredAt,
      payload: { operation: 'upsert', hall: data.room }, adminOnly: true,
    }));
  }
  const employee = path.match(/^\/api\/v1\/admin\/employees(?:\/(\d+))?$/);
  if (employee) {
    const employeeId = Number(employee[1] ?? (data.employee as any)?.id);
    patches.push(await patch(database, {
      type: request.method === 'DELETE' ? 'employee.deleted' : 'employee.changed',
      resource: 'employees', mutationId, occurredAt,
      payload: request.method === 'DELETE'
        ? { operation: 'delete', employeeId }
        : { operation: 'upsert', employee: data.employee },
      adminOnly: true,
    }));
    if (Number.isSafeInteger(employeeId)) {
      patches.push(await patch(database, {
        type: 'hall.assignments.changed',
        resource: `user:${employeeId}:assignments`, mutationId, occurredAt,
        payload: { operation: 'replace', employeeId,
          hallIds: (data.employee as any)?.roomIds ?? [] },
        audienceUserIds: [employeeId],
      }));
    }
  }
  return patches;
}

async function catalogPatch(database: D1Database, request: Request,
  data: Record<string, unknown>, mutationId: string, occurredAt: string) {
  const path = new URL(request.url).pathname;
  let type = 'catalog.changed';
  let payload: Record<string, unknown> = { operation: request.method.toLowerCase() };
  let hallIds: number[] = [];
  let adminOnly = false;

  const hallMenus = path.match(/^\/api\/v1\/admin\/halls\/(\d+)\/menus$/);
  if (hallMenus) {
    hallIds = [Number(hallMenus[1])];
    type = 'hall.catalog.changed';
    payload = { operation: 'replace-menu-assignments', ...data };
  } else if (path === '/api/v1/admin/ingredient-categories') {
    type = 'catalog.ingredient-category.created';
    payload = { operation: 'upsert', category: data.category };
    adminOnly = true;
  } else if (path === '/api/v1/admin/ingredients') {
    type = 'catalog.ingredient.created';
    payload = { operation: 'upsert', ingredient: data.ingredient };
    adminOnly = true;
  } else if (path === '/api/v1/admin/menus') {
    type = 'catalog.menu.created';
    payload = { operation: 'upsert', menu: data.menu };
    hallIds = assignmentHallIds(data.menu);
  } else {
    const menuCategory = path.match(/^\/api\/v1\/admin\/menus\/(\d+)\/categories$/);
    const menuOrder = path.match(/^\/api\/v1\/admin\/menus\/(\d+)\/category-order$/);
    const menuProduct = path.match(/^\/api\/v1\/admin\/menus\/(\d+)\/products$/);
    const category = path.match(/^\/api\/v1\/admin\/categories\/(\d+)$/);
    const productOrder = path.match(/^\/api\/v1\/admin\/categories\/(\d+)\/product-order$/);
    const product = path.match(/^\/api\/v1\/admin\/products\/(\d+)$/);
    if (menuCategory || menuOrder) {
      const menuId = Number((menuCategory ?? menuOrder)![1]);
      type = menuCategory ? 'catalog.category.created' : 'catalog.category-order.changed';
      payload = menuCategory ? { operation: 'upsert', category: data.category } :
        { operation: 'reorder', ...data };
      hallIds = await menuHallIds(database, menuId);
    } else if (menuProduct) {
      type = 'catalog.product.created';
      payload = { operation: 'upsert', product: data.product };
      hallIds = await productAudience(database, Number((data.product as any)?.id));
    } else if (category || productOrder) {
      const categoryId = Number((category ?? productOrder)![1]);
      const row = await database.prepare(
        'SELECT menu_id AS menuId FROM menu_categories WHERE id = ?',
      ).bind(categoryId).first<{ menuId: number }>();
      type = category ? 'catalog.category.changed' : 'catalog.product-order.changed';
      payload = category ? { operation: 'upsert', category: data.category } :
        { operation: 'reorder', categoryId, ...data };
      hallIds = row ? await menuHallIds(database, row.menuId) : [];
    } else if (product) {
      const productId = Number(product[1]);
      type = request.method === 'DELETE'
        ? 'catalog.product.deactivated' : 'catalog.product.changed';
      payload = request.method === 'DELETE'
        ? { operation: 'deactivate', productId }
        : { operation: 'upsert', product: data.product };
      hallIds = await productAudience(database, productId);
    } else {
      return undefined;
    }
  }
  return patch(database, {
    type, resource: 'catalog', mutationId, occurredAt, payload,
    audienceHallIds: hallIds, adminOnly,
  });
}

async function patch(database: D1Database, value: Omit<RealtimePatch, 'version'>) {
  const row = await database.prepare(
    'SELECT version FROM resource_versions WHERE resource = ?',
  ).bind(value.resource).first<{ version: number }>();
  return { ...value, version: Number(row?.version ?? 0) };
}

async function menuHallIds(database: D1Database, menuId: number) {
  return (await rows<{ hallId: number }>(database.prepare(
    'SELECT hall_id AS hallId FROM menu_halls WHERE menu_id = ?',
  ).bind(menuId))).map((row) => row.hallId);
}

async function productAudience(database: D1Database, productId: number) {
  if (!Number.isSafeInteger(productId)) return [];
  return (await rows<{ hallId: number }>(database.prepare(
    `SELECT DISTINCT mh.hall_id AS hallId FROM products p
     JOIN menu_halls mh ON mh.menu_id = p.menu_id
     LEFT JOIN product_halls ph ON ph.product_id = p.id AND ph.hall_id = mh.hall_id
     WHERE p.id = ? AND (mh.is_primary = 1 OR ph.hall_id IS NOT NULL)`,
  ).bind(productId))).map((row) => row.hallId);
}

function assignmentHallIds(value: unknown) {
  if (!isObject(value) || !Array.isArray(value.hallAssignments)) return [];
  return value.hallAssignments.map((item) => isObject(item) ? Number(item.hallId) : NaN)
    .filter(Number.isSafeInteger);
}

function isObject(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function numberFromPath(path: string, expression: RegExp) {
  const value = path.match(expression)?.[1];
  return value === undefined ? undefined : Number(value);
}
