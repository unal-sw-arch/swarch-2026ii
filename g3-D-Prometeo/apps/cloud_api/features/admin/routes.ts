import { Hono } from 'hono';
import { hashPassword } from '../../domain/security/password';
import { requireAdmin, requireSession } from '../auth/session';
import { readLayout } from '../read_model/repository';
import { bogotaMetricKeys, rebuildSalesMetricStatements,
  rebuildStatusMetricStatements, billedOrderMetricStatements,
  statusMetricChange } from './sales_metrics';
import { generatedId, jsonObject, positiveId, rows } from '../../shared/http';
import type { CloudEnvironment } from '../../types';
import {
  bogotaParts, employeeInput, employeeWithHalls, hallSummary, hallsExist, idArray,
  localIso, parseEditorLayout, revenueBuckets,
} from './helpers';

const allowedRoles = new Set(['waiter', 'kitchen', 'cashier', 'manager']);
const routes = new Hono<CloudEnvironment>();
routes.use('*', requireSession, requireAdmin);

routes.get('/activities', async (c) => c.json({
  activities: await rows(c.env.DB.prepare(
    `SELECT a.id, COALESCE(u.name, 'System') AS author, a.type,
            a.modification, a.hall_id AS roomId, a.created_at AS createdAt
     FROM activity_log a LEFT JOIN users u ON u.id = a.author_id
     ORDER BY a.id DESC LIMIT 100`,
  )),
}));

routes.get('/halls/:hallId/order-history-summary', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await c.env.DB.prepare('SELECT 1 FROM hall WHERE id = ?')
    .bind(hallId).first()) return c.json({ error: 'ROOM_NOT_FOUND' }, 404);
  const counts = await rows<{ status: string; count: number }>(c.env.DB.prepare(
    `SELECT status, order_count AS count FROM hall_order_status_totals
     WHERE hall_id = ?`,
  ).bind(hallId));
  const count = (status: string) => Number(
    counts.find((row) => row.status === status)?.count ?? 0,
  );
  return c.json({
    hallId,
    waiting: count('waiting'),
    eating: count('eating'),
    closed: count('closed'),
  });
});

routes.get('/halls/:hallId/order-history', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await c.env.DB.prepare('SELECT 1 FROM hall WHERE id = ?')
    .bind(hallId).first()) return c.json({ error: 'ROOM_NOT_FOUND' }, 404);
  const status = c.req.query('status');
  if (status !== undefined && !['waiting', 'eating', 'closed'].includes(status)) {
    return c.json({ error: 'INVALID_ORDER_STATUS' }, 422);
  }
  const requestedLimit = Number(c.req.query('limit'));
  const limit = Number.isSafeInteger(requestedLimit)
    ? Math.max(1, Math.min(100, requestedLimit)) : 50;
  const entries = await rows(c.env.DB.prepare(
    `SELECT o.id, o.status, o.total_value AS totalValue,
            o.payment_method AS paymentMethod, o.updated_at AS updatedAt,
            COALESCE(o.external_name, g.visible_identifier, t.identifier,
              'Pedido externo') AS label
     FROM orders o
     LEFT JOIN hall_tables t ON t.id = o.table_id
     LEFT JOIN table_groups g ON g.id = o.table_group_id
     WHERE o.hall_id = ? ${status === undefined ? '' : 'AND o.status = ?'}
     ORDER BY o.updated_at DESC, o.id DESC LIMIT ?`,
  ).bind(...(status === undefined ? [hallId, limit] : [hallId, status, limit])));
  return c.json({ entries });
});

routes.delete('/halls/:hallId/orders/:orderId', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  const orderId = positiveId(c.req.param('orderId'));
  if (!hallId || !orderId) return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  const order = await c.env.DB.prepare(
    `SELECT id, status, updated_at AS updatedAt
     FROM orders WHERE id = ? AND hall_id = ?`,
  ).bind(orderId, hallId).first<{ id: number; status: string; updatedAt: string }>();
  if (!order) return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  const salesChanges = order.status === 'closed'
    ? await billedOrderMetricStatements(
      c.env.DB, orderId, hallId, new Date(order.updatedAt), -1,
    ) : [];
  const session = c.get('session');
  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare('DELETE FROM order_modifications WHERE order_id = ?').bind(orderId),
    c.env.DB.prepare('DELETE FROM order_items WHERE order_id = ?').bind(orderId),
    c.env.DB.prepare('DELETE FROM removed_order_items WHERE order_id = ?').bind(orderId),
    c.env.DB.prepare('DELETE FROM orders WHERE id = ?').bind(orderId),
    statusMetricChange(c.env.DB, hallId, order.status, -1),
    ...salesChanges,
    c.env.DB.prepare(
      `UPDATE hall_tables SET status = COALESCE((
         SELECT active.status FROM orders active
         LEFT JOIN table_group_members member ON member.group_id = active.table_group_id
         WHERE active.status IN ('waiting', 'eating')
           AND (active.table_id = hall_tables.id OR member.table_id = hall_tables.id)
         ORDER BY CASE active.status WHEN 'waiting' THEN 0 ELSE 1 END LIMIT 1
       ), 'available') WHERE hall_id = ?`,
    ).bind(hallId),
    c.env.DB.prepare(
      `UPDATE table_groups SET status = COALESCE((
         SELECT active.status FROM orders active
         WHERE active.status IN ('waiting', 'eating')
           AND (active.table_group_id = table_groups.id OR (
             active.table_group_id IS NULL AND active.table_id IN (
               SELECT member.table_id FROM table_group_members member
               WHERE member.group_id = table_groups.id
             )
           ))
         ORDER BY CASE active.status WHEN 'waiting' THEN 0 ELSE 1 END LIMIT 1
       ), 'available') WHERE hall_id = ?`,
    ).bind(hallId),
    ...(order.status === 'closed' ? [c.env.DB.prepare(
      `INSERT INTO resource_versions(resource, version, updated_at)
       VALUES ('dashboard', 1, ?)
       ON CONFLICT(resource) DO UPDATE SET
         version = version + 1, updated_at = excluded.updated_at`,
    ).bind(now)] : []),
    c.env.DB.prepare(
      `INSERT INTO activity_log
       (author_id, hall_id, type, modification, created_at)
       VALUES (?, ?, 'Pedidos', ?, ?)`,
    ).bind(session.user.id, hallId, `Eliminó el pedido ${orderId}`, now),
  ]);
  return c.json({ deleted: true, orderId, status: order.status });
});

routes.delete('/halls/:hallId/orders', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await c.env.DB.prepare('SELECT 1 FROM hall WHERE id = ?')
    .bind(hallId).first()) return c.json({ error: 'ROOM_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const allowed = new Set(['waiting', 'eating', 'closed']);
  const statuses = Array.isArray(body.statuses)
    ? [...new Set(body.statuses.map(String))] : [];
  if (statuses.length === 0 || statuses.some((status) => !allowed.has(status))) {
    return c.json({ error: 'INVALID_ORDER_STATUSES' }, 422);
  }

  const placeholders = statuses.map(() => '?').join(',');
  const targetOrders = `SELECT o.id FROM orders o
    WHERE o.hall_id = ?
      AND o.status IN (${placeholders})`;
  const bindings = [hallId, ...statuses];
  const found = await c.env.DB.prepare(
    `SELECT COUNT(*) AS count FROM (${targetOrders}) selected`,
  ).bind(...bindings).first<{ count: number }>();
  const deletedCount = Number(found?.count ?? 0);
  if (deletedCount === 0) return c.json({ deletedCount: 0, statuses });

  const session = c.get('session');
  const now = new Date().toISOString();
  const labels: Record<string, string> = {
    waiting: 'pendientes', eating: 'entregadas', closed: 'facturadas',
  };
  await c.env.DB.batch([
    c.env.DB.prepare(
      `DELETE FROM order_modifications WHERE order_id IN (${targetOrders})`,
    ).bind(...bindings),
    c.env.DB.prepare(
      `DELETE FROM order_items WHERE order_id IN (${targetOrders})`,
    ).bind(...bindings),
    c.env.DB.prepare(
      `DELETE FROM removed_order_items WHERE order_id IN (${targetOrders})`,
    ).bind(...bindings),
    c.env.DB.prepare(
      `DELETE FROM orders WHERE id IN (${targetOrders})`,
    ).bind(...bindings),
    ...rebuildStatusMetricStatements(c.env.DB),
    ...(statuses.includes('closed') ? rebuildSalesMetricStatements(c.env.DB) : []),
    c.env.DB.prepare(
      `UPDATE hall_tables SET status = COALESCE((
         SELECT o.status FROM orders o
         LEFT JOIN table_group_members member ON member.group_id = o.table_group_id
         WHERE o.status IN ('waiting', 'eating')
           AND (o.table_id = hall_tables.id OR member.table_id = hall_tables.id)
         ORDER BY CASE o.status WHEN 'waiting' THEN 0 ELSE 1 END LIMIT 1
       ), 'available') WHERE hall_id = ?`,
    ).bind(hallId),
    c.env.DB.prepare(
      `UPDATE table_groups SET status = COALESCE((
         SELECT o.status FROM orders o
         WHERE o.status IN ('waiting', 'eating')
           AND (o.table_group_id = table_groups.id OR (
             o.table_group_id IS NULL AND o.table_id IN (
               SELECT member.table_id FROM table_group_members member
               WHERE member.group_id = table_groups.id
             )
           ))
         ORDER BY CASE o.status WHEN 'waiting' THEN 0 ELSE 1 END LIMIT 1
       ), 'available') WHERE hall_id = ?`,
    ).bind(hallId),
    ...(statuses.includes('closed') ? [c.env.DB.prepare(
      `INSERT INTO resource_versions(resource, version, updated_at)
       VALUES ('dashboard', 1, ?)
       ON CONFLICT(resource) DO UPDATE SET
         version = version + 1, updated_at = excluded.updated_at`,
    ).bind(now)] : []),
    c.env.DB.prepare(
      `INSERT INTO activity_log
       (author_id, hall_id, type, modification, created_at)
       VALUES (?, ?, 'Pedidos', ?, ?)`,
    ).bind(session.user.id, hallId,
      `Eliminó ${deletedCount} orden(es) ${statuses.map((status) => labels[status]).join(', ')}`,
      now),
  ]);
  return c.json({ deletedCount, statuses });
});

routes.get('/overview', async (c) => {
  const period = ['minute', 'hour', 'day', 'month', 'year']
    .includes(c.req.query('period') ?? '')
    ? c.req.query('period') as 'minute' | 'hour' | 'day' | 'month' | 'year' : 'day';
  const requested = Number(c.req.query('range'));
  const limits = period === 'minute' ? [2, 30] : period === 'day' ? [2, 31] : period === 'month'
    ? [2, 24] : period === 'year' ? [2, 10] : [24, 24];
  const range = Number.isSafeInteger(requested)
    ? Math.max(limits[0], Math.min(limits[1], requested))
    : period === 'minute' ? 30 : period === 'day' ? 7
      : period === 'month' ? 6 : period === 'year' ? 5 : 24;
  const now = new Date();
  const today = bogotaParts(now);
  const todayStart = localIso(today.year, today.month, today.day);
  const buckets = revenueBuckets(period, range, todayStart, now);
  const keys = buckets.map((bucket) => bucket.key);
  const todayKey = bogotaMetricKeys(now).day;
  const [summary, accumulatedPoints, topProduct, categories] = await Promise.all([
    c.env.DB.prepare(
      `SELECT total_sales AS sales, order_count AS orders
       FROM sales_metric_buckets WHERE period = 'day' AND bucket_key = ?`,
    ).bind(todayKey).first<{ sales: number; orders: number }>(),
    rows<{ key: string; value: number }>(c.env.DB.prepare(
      `SELECT bucket_key AS key, total_sales AS value
       FROM sales_metric_buckets
       WHERE period = ? AND bucket_key IN (${keys.map(() => '?').join(',')})`,
    ).bind(period, ...keys)),
    c.env.DB.prepare(
      `SELECT product_name AS name, unit_value AS value, quantity
       FROM daily_product_sales WHERE day_key = ?
       ORDER BY quantity DESC, product_name COLLATE NOCASE, product_id LIMIT 1`,
    ).bind(todayKey).first(),
    rows(c.env.DB.prepare(
      `SELECT category_name AS name, total_sales AS value
       FROM daily_category_sales WHERE day_key = ?
       ORDER BY total_sales DESC, category_name COLLATE NOCASE, category_id LIMIT 4`,
    ).bind(todayKey)),
  ]);
  const values = new Map(accumulatedPoints.map((point) => [point.key, Number(point.value)]));
  const points = buckets.map((bucket) => ({
    label: bucket.label,
    value: values.get(bucket.key) ?? 0,
  }));
  const sales = Number(summary?.sales ?? 0);
  const orderCount = Number(summary?.orders ?? 0);
  return c.json({
    salesToday: sales,
    ordersToday: orderCount,
    averageTicket: orderCount === 0 ? 0 : Math.round(sales / orderCount),
    points,
    topProduct,
    categories,
  });
});

routes.post('/halls', async (c) => {
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = typeof body.name === 'string' ? body.name.trim() : '';
  if (name.length < 2 || name.length > 80) {
    return c.json({ error: 'INVALID_ROOM_NAME' }, 422);
  }
  const id = generatedId();
  try {
    await c.env.DB.prepare('INSERT INTO hall (id, name) VALUES (?, ?)').bind(id, name).run();
  } catch (error) {
    if (String(error).includes('UNIQUE')) return c.json({ error: 'ROOM_NAME_TAKEN' }, 409);
    throw error;
  }
  return c.json({ room: hallSummary(id, name) }, 201);
});

routes.get('/employees', async (c) => {
  const employees = await rows<{ id: number; fullName: string; username: string; role: string }>(
    c.env.DB.prepare(
      `SELECT id, name AS fullName, username, role FROM users
       WHERE role != 'admin' ORDER BY name COLLATE NOCASE`,
    ),
  );
  return c.json({ employees: await Promise.all(employees.map(
    (employee) => employeeWithHalls(c.env.DB, employee),
  )) });
});

routes.post('/employees', async (c) => {
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const parsed = employeeInput(body, true);
  if (!parsed || !await hallsExist(c.env.DB, parsed.roomIds)) {
    return c.json({ error: 'VALIDATION_ERROR' }, 422);
  }
  const id = generatedId();
  const statements = [
    c.env.DB.prepare(
      'INSERT INTO users (id, name, role, username, password_hash) VALUES (?, ?, ?, ?, ?)',
    ).bind(id, parsed.fullName, parsed.role, parsed.username,
      await hashPassword(parsed.password!)),
    ...parsed.roomIds.map((hallId) => c.env.DB.prepare(
      'INSERT INTO employee_halls (user_id, hall_id) VALUES (?, ?)',
    ).bind(id, hallId)),
  ];
  try {
    await c.env.DB.batch(statements);
  } catch (error) {
    if (String(error).includes('UNIQUE')) return c.json({ error: 'USERNAME_TAKEN' }, 409);
    throw error;
  }
  return c.json({ employee: await employeeWithHalls(c.env.DB, {
    id, fullName: parsed.fullName, username: parsed.username, role: parsed.role,
  }) }, 201);
});

routes.patch('/employees/:employeeId', async (c) => {
  const id = positiveId(c.req.param('employeeId'));
  const existing = id ? await c.env.DB.prepare(
    "SELECT id, name AS fullName, username, role FROM users WHERE id = ? AND role != 'admin'",
  ).bind(id).first<{ id: number; fullName: string; username: string; role: string }>() : null;
  if (!id || !existing) return c.json({ error: 'NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const role = body.role === undefined ? existing.role : String(body.role);
  const roomIds = body.roomIds === undefined ? undefined : idArray(body.roomIds);
  const password = body.password === undefined ? undefined : String(body.password);
  if (!allowedRoles.has(role) || roomIds === null ||
      (password !== undefined && (password.length < 12 || password.length > 128)) ||
      (roomIds !== undefined && !await hallsExist(c.env.DB, roomIds))) {
    return c.json({ error: 'VALIDATION_ERROR' }, 422);
  }
  const statements: D1PreparedStatement[] = [
    c.env.DB.prepare('UPDATE users SET role = ? WHERE id = ?').bind(role, id),
  ];
  if (password !== undefined) statements.push(c.env.DB.prepare(
    'UPDATE users SET password_hash = ? WHERE id = ?',
  ).bind(await hashPassword(password), id));
  if (roomIds !== undefined) {
    statements.push(c.env.DB.prepare('DELETE FROM employee_halls WHERE user_id = ?').bind(id));
    statements.push(...roomIds.map((hallId) => c.env.DB.prepare(
      'INSERT INTO employee_halls (user_id, hall_id) VALUES (?, ?)',
    ).bind(id, hallId)));
  }
  await c.env.DB.batch(statements);
  return c.json({ employee: await employeeWithHalls(c.env.DB, { ...existing, role }) });
});

routes.delete('/employees/:employeeId', async (c) => {
  const id = positiveId(c.req.param('employeeId'));
  if (!id) return c.json({ error: 'NOT_FOUND' }, 404);
  const result = await c.env.DB.prepare(
    "DELETE FROM users WHERE id = ? AND role != 'admin'",
  ).bind(id).run();
  return result.meta.changes === 0
    ? c.json({ error: 'NOT_FOUND' }, 404) : c.body(null, 204);
});

routes.put('/halls/:hallId/layout', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await c.env.DB.prepare('SELECT 1 FROM hall WHERE id = ?')
    .bind(hallId).first()) return c.json({ error: 'ROOM_NOT_FOUND' }, 404);
  if (await c.env.DB.prepare(
    `SELECT 1 FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
     WHERE o.status != 'closed' AND COALESCE(o.hall_id, t.hall_id) = ? LIMIT 1`,
  ).bind(hallId).first()) return c.json({ error: 'HALL_HAS_ACTIVE_ORDERS' }, 409);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const layout = parseEditorLayout(body);
  if (!layout) return c.json({ error: 'INVALID_LAYOUT' }, 422);
  const existing = new Set((await rows<{ id: number }>(c.env.DB.prepare(
    'SELECT id FROM hall_tables WHERE hall_id = ?',
  ).bind(hallId))).map(({ id }) => id));
  if (layout.tables.some((table) => table.id > 0 && !existing.has(table.id))) {
    return c.json({ error: 'INVALID_LAYOUT' }, 422);
  }
  const idMap = new Map<number, number>();
  layout.tables.forEach((table) => idMap.set(
    table.id, table.id > 0 ? table.id : generatedId(),
  ));
  const retained = new Set(layout.tables.filter((table) => table.id > 0).map((table) => table.id));
  const statements: D1PreparedStatement[] = [
    c.env.DB.prepare(
      `DELETE FROM table_group_members WHERE group_id IN
       (SELECT id FROM table_groups WHERE hall_id = ?)`,
    ).bind(hallId),
    c.env.DB.prepare('DELETE FROM hall_walls WHERE hall_id = ?').bind(hallId),
    ...[...existing].filter((id) => !retained.has(id)).map((id) =>
      c.env.DB.prepare('DELETE FROM hall_tables WHERE id = ?').bind(id)),
  ];
  for (const table of layout.tables) {
    const id = idMap.get(table.id)!;
    statements.push(table.id > 0
      ? c.env.DB.prepare(
        `UPDATE hall_tables SET identifier = ?, x = ?, y = ?, width = ?, height = ?, rotation = ?
         WHERE id = ? AND hall_id = ?`,
      ).bind(table.identifier, table.x, table.y, table.width, table.height,
        table.rotation, id, hallId)
      : c.env.DB.prepare(
        `INSERT INTO hall_tables
         (id, identifier, x, y, width, height, rotation, status, hall_id)
         VALUES (?, ?, ?, ?, ?, ?, ?, 'available', ?)`,
      ).bind(id, table.identifier, table.x, table.y, table.width, table.height,
        table.rotation, hallId));
  }
  statements.push(...layout.walls.map((wall) => c.env.DB.prepare(
    `INSERT INTO hall_walls (id, hall_id, x, y, width, height, rotation)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).bind(generatedId(), hallId, wall.x, wall.y, wall.width, wall.height, wall.rotation)));
  for (const group of layout.groups) {
    const groupId = group.id > 0 ? group.id : generatedId();
    const known = group.id > 0 && await c.env.DB.prepare(
      'SELECT 1 FROM table_groups WHERE id = ? AND hall_id = ?',
    ).bind(group.id, hallId).first();
    statements.push(known
      ? c.env.DB.prepare('UPDATE table_groups SET visible_identifier = ? WHERE id = ?')
        .bind(group.identifier, groupId)
      : c.env.DB.prepare(
        `INSERT INTO table_groups
         (id, hall_id, visible_identifier, status, created_at)
         VALUES (?, ?, ?, 'available', ?)`,
      ).bind(groupId, hallId, group.identifier, new Date().toISOString()));
    statements.push(...group.tableIds.map((tableId) => c.env.DB.prepare(
      'INSERT INTO table_group_members (group_id, table_id) VALUES (?, ?)',
    ).bind(groupId, idMap.get(tableId)!)));
  }
  try {
    await c.env.DB.batch(statements);
  } catch (error) {
    if (String(error).includes('constraint')) return c.json({ error: 'LAYOUT_SAVE_FAILED' }, 409);
    throw error;
  }
  return c.json(await readLayout(c.env.DB, hallId));
});

export default routes;
