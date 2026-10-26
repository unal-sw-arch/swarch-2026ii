import { Hono } from 'hono';
import { canAccessHall, requireSession } from '../auth/session';
import { readHallOrders, readOrder, tableLabel } from './repository';
import { billedOrderMetricStatements, statusMetricChange,
  statusMetricTransition } from '../admin/sales_metrics';
import { splitChargeSubtotal } from './split_payments';
import { generatedId, jsonObject, positiveId, rows } from '../../shared/http';
import type { CloudEnvironment } from '../../types';
import { parseOrderInput } from './validation';
import {
  accessibleHall, activeOrder, activeOrderAt, activity, bogotaBusinessDay,
  buildReplacement, hasPendingItems, itemStatements, keepsDelivered, modification,
  parseSplitAccounts, paymentMethods, refreshOrderTotal, tableTarget,
  targetStatusStatements,
} from './helpers';

const routes = new Hono<CloudEnvironment>();
routes.use('*', requireSession);

routes.get('/halls/:hallId/menus', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const menus = await rows<{ id: number; name: string; isPrimary: number }>(c.env.DB.prepare(
    `SELECT m.id, m.name, mh.is_primary AS isPrimary FROM menu m
     JOIN menu_halls mh ON mh.menu_id = m.id
     WHERE mh.hall_id = ? ORDER BY mh.is_primary DESC, m.name COLLATE NOCASE`,
  ).bind(hallId));
  const catalog = await Promise.all(menus.map(async (menu) => {
    const categories = await rows<Record<string, unknown>>(c.env.DB.prepare(
      `SELECT id, name, parent_category_id AS parentCategoryId,
              is_special AS isSpecial, position
       FROM menu_categories WHERE menu_id = ?
       ORDER BY position, name COLLATE NOCASE, id`,
    ).bind(menu.id));
    const productSql = menu.isPrimary === 1
      ? `SELECT p.id, p.name, p.description, p.value, p.category_id AS categoryId,
                ordering.position
         FROM products p
         LEFT JOIN category_product_positions ordering
           ON ordering.category_id = p.category_id AND ordering.product_id = p.id
         WHERE p.menu_id = ? AND p.is_active = 1
         ORDER BY p.category_id, ordering.position IS NULL, ordering.position,
                  p.name COLLATE NOCASE, p.id`
      : `SELECT p.id, p.name, p.description, p.value, p.category_id AS categoryId,
                ordering.position
         FROM product_halls ph
         JOIN products p ON p.id = ph.product_id
         LEFT JOIN category_product_positions ordering
           ON ordering.category_id = p.category_id AND ordering.product_id = p.id
         WHERE ph.hall_id = ? AND p.menu_id = ? AND p.is_active = 1
         ORDER BY p.category_id, ordering.position IS NULL, ordering.position,
                  p.name COLLATE NOCASE, p.id`;
    const productBindings = menu.isPrimary === 1 ? [menu.id] : [hallId, menu.id];
    const products = await rows<Record<string, unknown> & { id: number; categoryId: number }>(
      c.env.DB.prepare(productSql).bind(...productBindings),
    );
    const ingredientSql = menu.isPrimary === 1
      ? `SELECT pi.product_id AS productId, i.id, i.name, i.description
         FROM products p
         JOIN product_ingredients pi ON pi.product_id = p.id
         JOIN ingredients i ON i.id = pi.ingredient_id
         WHERE p.menu_id = ? AND p.is_active = 1
         ORDER BY pi.product_id, i.name COLLATE NOCASE`
      : `SELECT pi.product_id AS productId, i.id, i.name, i.description
         FROM product_halls ph
         JOIN products p ON p.id = ph.product_id
         JOIN product_ingredients pi ON pi.product_id = p.id
         JOIN ingredients i ON i.id = pi.ingredient_id
         WHERE ph.hall_id = ? AND p.menu_id = ? AND p.is_active = 1
         ORDER BY pi.product_id, i.name COLLATE NOCASE`;
    const ingredients = products.length === 0 ? [] : await rows<{
      productId: number; id: number; name: string; description: string | null;
    }>(c.env.DB.prepare(ingredientSql).bind(...productBindings));
    return {
      id: menu.id, name: menu.name, isPrimary: menu.isPrimary === 1,
      categories: categories.map((category) => ({
        ...category,
        isSpecial: category.isSpecial === 1,
        products: products.filter((product) => product.categoryId === category.id)
          .map((product) => ({ ...product, ingredients: ingredients.filter(
            (ingredient) => ingredient.productId === product.id,
          ).map(({ productId: _, ...ingredient }) => ingredient) })),
      })),
    };
  }));
  return c.json({ menus: catalog });
});

routes.get('/halls/:hallId/orders', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  return c.json({ orders: await readHallOrders(c.env.DB, hallId) });
});

routes.get('/halls/:hallId/orders/today', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const fromQuery = c.req.query('from');
  const toQuery = c.req.query('to');
  const paymentMethod = c.req.query('paymentMethod');
  if ((fromQuery === undefined) !== (toQuery === undefined) ||
      (paymentMethod !== undefined && !paymentMethods.has(paymentMethod))) {
    return c.json({ error: 'INVALID_HISTORY_FILTER' }, 422);
  }
  let from = fromQuery;
  let to = toQuery;
  if (from && to) {
    const start = new Date(from);
    const end = new Date(to);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start >= end) {
      return c.json({ error: 'INVALID_HISTORY_FILTER' }, 422);
    }
    from = start.toISOString();
    to = end.toISOString();
  } else {
    const range = bogotaBusinessDay(new Date());
    from = range.from;
    to = range.to;
  }
  const orders = (await readHallOrders(c.env.DB, hallId, true, from, to, paymentMethod))
    .filter((order) => order !== undefined);
  const inRange = (account: { paidAt: string | null; paymentMethod: string | null;
    status: string; totalValue: number }) => account.status === 'paid' &&
    account.paidAt !== null && account.paidAt >= from! && account.paidAt < to! &&
    (paymentMethod === undefined || account.paymentMethod === paymentMethod);
  return c.json({
    orders,
    total: orders.reduce((sum, order) => sum + order.splitAccounts
      .filter(inRange).reduce((amount, account) => amount + Number(account.totalValue), 0), 0),
  });
});

routes.post('/halls/:hallId/tables/:tableId/orders', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const tableId = positiveId(c.req.param('tableId'));
  const target = tableId ? await tableTarget(c.env.DB, hallId, tableId) : null;
  if (!tableId || !target) return c.json({ error: 'HALL_OR_TABLE_NOT_ASSIGNED' }, 403);
  if (await activeOrderAt(c.env.DB, tableId, target.groupId)) {
    return c.json({ error: 'ACTIVE_ORDER_EXISTS' }, 409);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const input = await parseOrderInput(c.env.DB, body, hallId);
  if (!input) return c.json({ error: 'INVALID_ORDER' }, 422);
  const orderId = generatedId();
  const now = new Date().toISOString();
  const session = c.get('session');
  const label = await tableLabel(c.env.DB, tableId, target.groupId);
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO orders
       (id, author_id, table_id, table_group_id, hall_id, description, receiver,
        status, created_at, updated_at, total_value)
       VALUES (?, ?, ?, ?, ?, ?, NULL, 'waiting', ?, ?, 0)`,
    ).bind(orderId, session.user.id, tableId, target.groupId, hallId,
      input.description, now, now),
    ...itemStatements(c.env.DB, orderId, input.items),
    refreshOrderTotal(c.env.DB, orderId),
    modification(c.env.DB, orderId, null, session.user.id, 'create', null,
      JSON.stringify(input), now),
    ...targetStatusStatements(c.env.DB, tableId, target.groupId, 'waiting'),
    activity(c.env.DB, session.user.id, hallId, 'Pedido',
      `Creó un pedido en la mesa ${label}`, now),
    statusMetricChange(c.env.DB, hallId, 'waiting', 1),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) }, 201);
});

routes.post('/halls/:hallId/external-orders', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const externalName = typeof body.externalName === 'string'
    ? body.externalName.trim().slice(0, 80) : '';
  if (!externalName) return c.json({ error: 'EXTERNAL_NAME_REQUIRED' }, 422);
  const input = await parseOrderInput(c.env.DB, body, hallId);
  if (!input) return c.json({ error: 'INVALID_ORDER' }, 422);
  const orderId = generatedId();
  const now = new Date().toISOString();
  const session = c.get('session');
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO orders
       (id, author_id, table_id, table_group_id, hall_id, external_name,
        description, receiver, status, created_at, updated_at, total_value)
       VALUES (?, ?, NULL, NULL, ?, ?, ?, NULL, 'waiting', ?, ?, 0)`,
    ).bind(orderId, session.user.id, hallId, externalName, input.description, now, now),
    ...itemStatements(c.env.DB, orderId, input.items),
    refreshOrderTotal(c.env.DB, orderId),
    modification(c.env.DB, orderId, null, session.user.id, 'create', null,
      JSON.stringify(input), now),
    activity(c.env.DB, session.user.id, hallId, 'Pedido',
      `Creó el pedido externo ${externalName}`, now),
    statusMetricChange(c.env.DB, hallId, 'waiting', 1),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) }, 201);
});

routes.put('/halls/:hallId/orders/:orderId', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const current = orderId ? await activeOrder(c.env.DB, hallId, orderId) : null;
  if (!orderId || !current) return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const input = await parseOrderInput(c.env.DB, body, hallId, true);
  if (!input) return c.json({ error: 'INVALID_ORDER' }, 422);
  const oldOrder = await readOrder(c.env.DB, orderId);
  if (!oldOrder || !keepsDelivered(oldOrder.items, input.items)) {
    return c.json({ error: 'DELIVERED_ITEM_CANNOT_BE_REMOVED' }, 409);
  }
  if (oldOrder.splitAccounts.some((account) => account.status === 'paid')) {
    return c.json({ error: 'PAID_SPLIT_CANNOT_BE_EDITED' }, 409);
  }
  const session = c.get('session');
  const now = new Date().toISOString();
  const label = await tableLabel(c.env.DB, current.tableId, current.tableGroupId,
    current.externalName);
  if (input.items.length === 0) {
    await c.env.DB.batch([
      c.env.DB.prepare('DELETE FROM order_modifications WHERE order_id = ?').bind(orderId),
      c.env.DB.prepare('DELETE FROM order_items WHERE order_id = ?').bind(orderId),
      c.env.DB.prepare('DELETE FROM removed_order_items WHERE order_id = ?').bind(orderId),
      c.env.DB.prepare('DELETE FROM orders WHERE id = ?').bind(orderId),
      ...(current.tableId === null ? [] : targetStatusStatements(
        c.env.DB, current.tableId, current.tableGroupId, 'available')),
      activity(c.env.DB, session.user.id, hallId, 'Pedido',
        `Eliminó el pedido pendiente de ${label}`, now),
      statusMetricChange(c.env.DB, hallId, current.status, -1),
    ]);
    return c.json({ deleted: true });
  }
  const replacement = buildReplacement(c.env.DB, orderId, oldOrder.items, input.items, now);
  const nextStatus = replacement.pending ? 'waiting' : 'eating';
  await c.env.DB.batch([
    c.env.DB.prepare(
      `DELETE FROM order_split_accounts WHERE order_id = ? AND status = 'draft'`,
    ).bind(orderId),
    ...replacement.statements,
    c.env.DB.prepare(
      `UPDATE orders SET description = ?, status = ?, updated_at = ?,
         total_value = COALESCE((SELECT SUM(unit_value * quantity)
           FROM order_items WHERE order_id = ?), 0)
       WHERE id = ?`,
    ).bind(input.description, nextStatus, now, orderId, orderId),
    ...(current.tableId === null ? [] : targetStatusStatements(
      c.env.DB, current.tableId, current.tableGroupId, nextStatus)),
    modification(c.env.DB, orderId, null, session.user.id, 'update',
      JSON.stringify(oldOrder), JSON.stringify(input), now),
    activity(c.env.DB, session.user.id, hallId, 'Pedido',
      `Modificó el pedido de ${label}`, now),
    ...statusMetricTransition(c.env.DB, hallId, current.status, nextStatus),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

routes.patch('/halls/:hallId/orders/:orderId/transfer', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const current = orderId ? await activeOrder(c.env.DB, hallId, orderId) : null;
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const tableId = positiveId(body.tableId);
  if (!orderId || !current || current.tableId === null) {
    return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  }
  const target = tableId ? await tableTarget(c.env.DB, hallId, tableId) : null;
  if (!tableId || !target || target.status !== 'available' ||
      await activeOrderAt(c.env.DB, tableId, target.groupId, orderId)) {
    return c.json({ error: 'TABLE_NOT_AVAILABLE' }, 409);
  }
  const session = c.get('session');
  const now = new Date().toISOString();
  const oldLabel = await tableLabel(c.env.DB, current.tableId, current.tableGroupId);
  const newLabel = await tableLabel(c.env.DB, tableId, target.groupId);
  await c.env.DB.batch([
    ...targetStatusStatements(c.env.DB, current.tableId, current.tableGroupId, 'available'),
    c.env.DB.prepare(
      `UPDATE orders SET table_id = ?, table_group_id = ?, updated_at = ? WHERE id = ?`,
    ).bind(tableId, target.groupId, now, orderId),
    ...targetStatusStatements(c.env.DB, tableId, target.groupId, current.status),
    modification(c.env.DB, orderId, null, session.user.id, 'transfer', oldLabel, newLabel, now),
    activity(c.env.DB, session.user.id, hallId, 'Mesa',
      `Trasladó el pedido de ${oldLabel} a ${newLabel}`, now),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

routes.patch('/halls/:hallId/orders/:orderId/status', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const current = orderId ? await activeOrder(c.env.DB, hallId, orderId) : null;
  if (!orderId || !current) return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const status = body.status;
  const paymentMethod = body.paymentMethod;
  if (status !== 'eating' && status !== 'closed') {
    return c.json({ error: 'INVALID_ORDER_STATUS' }, 422);
  }
  if (status === 'closed' && (typeof paymentMethod !== 'string' ||
      !paymentMethods.has(paymentMethod))) {
    return c.json({ error: 'INVALID_PAYMENT_METHOD' }, 422);
  }
  if ((status === 'eating' && current.status !== 'waiting') ||
      (status === 'closed' && current.status !== 'eating')) {
    return c.json({ error: status === 'closed'
      ? 'ORDER_NOT_READY_TO_BILL' : 'INVALID_ORDER_STATUS' }, 409);
  }
  if (await hasPendingItems(c.env.DB, orderId)) {
    return c.json({ error: 'ORDER_ITEMS_PENDING' }, 409);
  }
  if (status === 'closed' && await c.env.DB.prepare(
    `SELECT 1 FROM order_split_accounts
     WHERE order_id = ? AND status = 'paid' LIMIT 1`,
  ).bind(orderId).first()) {
    return c.json({ error: 'SPLIT_PAYMENT_REQUIRED' }, 409);
  }
  const session = c.get('session');
  const now = new Date().toISOString();
  const label = await tableLabel(c.env.DB, current.tableId, current.tableGroupId,
    current.externalName);
  const salesMetrics = status === 'closed'
    ? await billedOrderMetricStatements(c.env.DB, orderId, hallId, new Date(now))
    : [];
  const fullPaymentId = status === 'closed' ? generatedId() : null;
  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE orders SET status = ?, payment_method = ?, updated_at = ? WHERE id = ?`,
    ).bind(status, status === 'closed' ? paymentMethod : null, now, orderId),
    ...(current.tableId === null ? [] : targetStatusStatements(c.env.DB,
      current.tableId, current.tableGroupId, status === 'closed' ? 'available' : 'eating')),
    modification(c.env.DB, orderId, null, session.user.id, 'status', current.status, status, now),
    activity(c.env.DB, session.user.id, hallId, 'Mesa', status === 'closed'
      ? `Facturó ${label}` : `Marcó ${label} como comiendo`, now),
    ...statusMetricTransition(c.env.DB, hallId, current.status, status),
    ...salesMetrics,
    ...(fullPaymentId === null ? [] : [
      c.env.DB.prepare(
        `DELETE FROM order_split_accounts
         WHERE order_id = ? AND status = 'draft'`,
      ).bind(orderId),
      c.env.DB.prepare(
        `INSERT INTO order_split_accounts
         (id, order_id, name, position, status, payment_method, tip_percent,
          tip_value, subtotal, total_value, paid_at, created_at, updated_at)
         SELECT ?, id, 'Cuenta completa', 0, 'paid', ?, 0, 0,
                total_value, total_value, ?, ?, ?
         FROM orders WHERE id = ?`,
      ).bind(fullPaymentId, paymentMethod, now, now, now, orderId),
      c.env.DB.prepare(
        `INSERT INTO order_split_allocations(account_id, order_item_id, half_units)
         SELECT ?, id, quantity * 2 FROM order_items WHERE order_id = ?`,
      ).bind(fullPaymentId, orderId),
    ]),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

routes.put('/halls/:hallId/orders/:orderId/split', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const current = orderId ? await activeOrder(c.env.DB, hallId, orderId) : null;
  if (!orderId || !current || current.status !== 'eating') {
    return c.json({ error: 'ORDER_NOT_READY_TO_BILL' }, 409);
  }
  if (await hasPendingItems(c.env.DB, orderId)) {
    return c.json({ error: 'ORDER_ITEMS_PENDING' }, 409);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const accounts = parseSplitAccounts(body.accounts);
  if (!accounts) return c.json({ error: 'INVALID_SPLIT_ACCOUNTS' }, 422);

  const items = await rows<{ id: number; quantity: number }>(c.env.DB.prepare(
    'SELECT id, quantity FROM order_items WHERE order_id = ?',
  ).bind(orderId));
  const capacity = new Map(items.map((item) => [item.id, item.quantity * 2]));
  const paid = await rows<{ orderItemId: number; halfUnits: number }>(c.env.DB.prepare(
    `SELECT allocation.order_item_id AS orderItemId,
            SUM(allocation.half_units) AS halfUnits
     FROM order_split_allocations allocation
     JOIN order_split_accounts account ON account.id = allocation.account_id
     WHERE account.order_id = ? AND account.status = 'paid'
     GROUP BY allocation.order_item_id`,
  ).bind(orderId));
  const used = new Map(paid.map((row) => [row.orderItemId, Number(row.halfUnits)]));
  for (const account of accounts) {
    for (const allocation of account.allocations) {
      const maximum = capacity.get(allocation.orderItemId);
      if (maximum === undefined) return c.json({ error: 'INVALID_SPLIT_ITEM' }, 422);
      const next = (used.get(allocation.orderItemId) ?? 0) + allocation.halfUnits;
      if (next > maximum) return c.json({ error: 'SPLIT_ITEM_OVERALLOCATED' }, 409);
      used.set(allocation.orderItemId, next);
    }
  }

  const now = new Date().toISOString();
  const statements: D1PreparedStatement[] = [
    c.env.DB.prepare(
      `DELETE FROM order_split_accounts
       WHERE order_id = ? AND status = 'draft'`,
    ).bind(orderId),
  ];
  for (const account of accounts) {
    const id = generatedId();
    statements.push(c.env.DB.prepare(
      `INSERT INTO order_split_accounts
       (id, order_id, name, position, status, tip_percent, tip_value,
        subtotal, total_value, created_at, updated_at)
       VALUES (?, ?, ?, ?, 'draft', ?, ?, 0, 0, ?, ?)`,
    ).bind(id, orderId, account.name, account.position,
      account.tipPercent, account.customTip, now, now));
    statements.push(...account.allocations.map((allocation) => c.env.DB.prepare(
      `INSERT INTO order_split_allocations(account_id, order_item_id, half_units)
       VALUES (?, ?, ?)`,
    ).bind(id, allocation.orderItemId, allocation.halfUnits)));
  }
  const session = c.get('session');
  statements.push(modification(c.env.DB, orderId, null, session.user.id,
    'split', null, JSON.stringify(accounts), now));
  await c.env.DB.batch(statements);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

routes.post('/halls/:hallId/orders/:orderId/split/:accountId/pay', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const accountId = positiveId(c.req.param('accountId'));
  const current = orderId ? await activeOrder(c.env.DB, hallId, orderId) : null;
  if (!orderId || !accountId || !current || current.status !== 'eating') {
    return c.json({ error: 'ORDER_NOT_READY_TO_BILL' }, 409);
  }
  const account = await c.env.DB.prepare(
    `SELECT id, tip_percent AS tipPercent, tip_value AS customTip
     FROM order_split_accounts
     WHERE id = ? AND order_id = ? AND status = 'draft'`,
  ).bind(accountId, orderId).first<{
    id: number; tipPercent: number; customTip: number;
  }>();
  if (!account) return c.json({ error: 'SPLIT_ACCOUNT_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const paymentMethod = body.paymentMethod;
  if (typeof paymentMethod !== 'string' || !paymentMethods.has(paymentMethod)) {
    return c.json({ error: 'INVALID_PAYMENT_METHOD' }, 422);
  }
  const allocations = await rows<{
    orderItemId: number; halfUnits: number; paidHalfUnits: number;
    quantity: number; unitValue: number;
  }>(c.env.DB.prepare(
    `SELECT allocation.order_item_id AS orderItemId,
            allocation.half_units AS halfUnits,
            item.quantity,
            COALESCE(item.unit_value, product.value) AS unitValue,
            COALESCE((
              SELECT SUM(previous.half_units)
              FROM order_split_allocations previous
              JOIN order_split_accounts paid ON paid.id = previous.account_id
              WHERE previous.order_item_id = item.id AND paid.status = 'paid'
            ), 0) AS paidHalfUnits
     FROM order_split_allocations allocation
     JOIN order_items item ON item.id = allocation.order_item_id
     JOIN products product ON product.id = item.product_id
     WHERE allocation.account_id = ?`,
  ).bind(accountId));
  if (allocations.length === 0) {
    return c.json({ error: 'EMPTY_SPLIT_ACCOUNT' }, 409);
  }
  if (allocations.some((line) => Number(line.paidHalfUnits) +
      Number(line.halfUnits) > Number(line.quantity) * 2)) {
    return c.json({ error: 'SPLIT_ITEM_OVERALLOCATED' }, 409);
  }
  const tipPercent = Number.isSafeInteger(body.tipPercent)
    ? Math.max(0, Math.min(100, Number(body.tipPercent))) : account.tipPercent;
  const requestedCustomTip = Number(body.customTip);
  const customTip = Number.isSafeInteger(requestedCustomTip) && requestedCustomTip >= 0
    ? requestedCustomTip : account.customTip;
  const subtotal = splitChargeSubtotal(allocations);
  const halfUnits = allocations.reduce((sum, line) =>
    sum + Number(line.halfUnits), 0);
  const tipValue = customTip > 0 ? customTip : Math.round(subtotal * tipPercent / 100);
  const paidCoverage = await c.env.DB.prepare(
    `SELECT COALESCE(SUM(allocation.half_units), 0) AS halfUnits
     FROM order_split_allocations allocation
     JOIN order_split_accounts split ON split.id = allocation.account_id
     WHERE split.order_id = ? AND split.status = 'paid'`,
  ).bind(orderId).first<{ halfUnits: number }>();
  const available = await c.env.DB.prepare(
    `SELECT COALESCE(SUM(quantity * 2), 0) AS halfUnits
     FROM order_items WHERE order_id = ?`,
  ).bind(orderId).first<{ halfUnits: number }>();
  const closesOrder = Number(paidCoverage?.halfUnits ?? 0) + halfUnits ===
    Number(available?.halfUnits ?? 0);
  const now = new Date().toISOString();
  const session = c.get('session');
  const salesMetrics = closesOrder
    ? await billedOrderMetricStatements(c.env.DB, orderId, hallId, new Date(now)) : [];
  const otherMethods = closesOrder ? await rows<{ method: string }>(c.env.DB.prepare(
    `SELECT DISTINCT payment_method AS method FROM order_split_accounts
     WHERE order_id = ? AND status = 'paid' AND payment_method IS NOT NULL`,
  ).bind(orderId)) : [];
  const allMethods = new Set([...otherMethods.map((row) => row.method), paymentMethod]);
  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE order_split_accounts SET status = 'paid', payment_method = ?,
         tip_percent = ?, tip_value = ?, subtotal = ?, total_value = ?,
         paid_at = ?, updated_at = ? WHERE id = ?`,
    ).bind(paymentMethod, tipPercent, tipValue, subtotal, subtotal + tipValue,
      now, now, accountId),
    ...(closesOrder ? [
      c.env.DB.prepare(
        `DELETE FROM order_split_accounts
         WHERE order_id = ? AND status = 'draft'`,
      ).bind(orderId),
      c.env.DB.prepare(
        `UPDATE orders SET status = 'closed', payment_method = ?, updated_at = ?
         WHERE id = ?`,
      ).bind(allMethods.size === 1 ? paymentMethod : null, now, orderId),
      ...(current.tableId === null ? [] : targetStatusStatements(
        c.env.DB, current.tableId, current.tableGroupId, 'available')),
      ...statusMetricTransition(c.env.DB, hallId, current.status, 'closed'),
      ...salesMetrics,
    ] : []),
    modification(c.env.DB, orderId, null, session.user.id, 'split_payment',
      null, JSON.stringify({ accountId, paymentMethod, subtotal, tipValue }), now),
    activity(c.env.DB, session.user.id, hallId, 'Mesa', closesOrder
      ? `Completó el pago separado de ${await tableLabel(c.env.DB,
        current.tableId, current.tableGroupId, current.externalName)}`
      : `Registró un pago separado`, now),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

routes.patch(
  '/halls/:hallId/orders/:orderId/items/:itemId/units/:unitIndex/deliver',
  async (c) => changeDelivery(c, true),
);
routes.patch(
  '/halls/:hallId/orders/:orderId/items/:itemId/units/:unitIndex/undo-delivery',
  async (c) => changeDelivery(c, false),
);
routes.patch('/halls/:hallId/orders/:orderId/deliver-all', async (c) => {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const order = orderId ? await c.env.DB.prepare(
    `SELECT o.table_id AS tableId, o.table_group_id AS tableGroupId,
            o.external_name AS externalName, o.status
     FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
     WHERE o.id = ? AND COALESCE(o.hall_id, t.hall_id) = ?
       AND o.status != 'closed'`,
  ).bind(orderId, hallId).first<{
    tableId: number | null; tableGroupId: number | null;
    externalName: string | null; status: string;
  }>() : null;
  if (!orderId || !order) return c.json({ error: 'ORDER_NOT_FOUND' }, 404);
  const pending = await c.env.DB.prepare(
    `SELECT 1 FROM order_items
     WHERE order_id = ? AND delivered_quantity < quantity LIMIT 1`,
  ).bind(orderId).first();
  if (!pending) return c.json({ order: await readOrder(c.env.DB, orderId) });

  const session = c.get('session');
  const now = new Date().toISOString();
  const label = await tableLabel(c.env.DB, order.tableId, order.tableGroupId,
    order.externalName);
  await c.env.DB.batch([
    c.env.DB.prepare(
      `WITH RECURSIVE pending_units(item_id, unit_index, quantity) AS (
         SELECT id, delivered_quantity, quantity FROM order_items
         WHERE order_id = ? AND delivered_quantity < quantity
         UNION ALL
         SELECT item_id, unit_index + 1, quantity FROM pending_units
         WHERE unit_index + 1 < quantity
       )
       INSERT OR IGNORE INTO order_item_deliveries
       (order_item_id, unit_index, delivered_at, delivered_by)
       SELECT item_id, unit_index, ?, ? FROM pending_units`,
    ).bind(orderId, now, session.user.id),
    c.env.DB.prepare(
      `UPDATE order_items SET delivered_quantity = quantity, status = 'delivered'
       WHERE order_id = ? AND delivered_quantity < quantity`,
    ).bind(orderId),
    c.env.DB.prepare(
      `UPDATE orders SET status = 'eating', updated_at = ? WHERE id = ?`,
    ).bind(now, orderId),
    ...(order.tableId === null ? [] : targetStatusStatements(
      c.env.DB, order.tableId, order.tableGroupId, 'eating')),
    modification(c.env.DB, orderId, null, session.user.id, 'deliver_all',
      order.status, 'eating', now),
    activity(c.env.DB, session.user.id, hallId, 'Pedido',
      `Entregó todos los productos de ${label}`, now),
    ...statusMetricTransition(c.env.DB, hallId, order.status, 'eating'),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
});

async function changeDelivery(c: Parameters<typeof accessibleHall>[0], deliver: boolean) {
  const hallId = await accessibleHall(c);
  if (hallId instanceof Response) return hallId;
  const orderId = positiveId(c.req.param('orderId'));
  const itemId = positiveId(c.req.param('itemId'));
  const unitIndex = Number(c.req.param('unitIndex'));
  if (!orderId || !itemId || !Number.isSafeInteger(unitIndex) || unitIndex < 0) {
    return c.json({ error: 'ORDER_ITEM_NOT_FOUND' }, 404);
  }
  const item = await c.env.DB.prepare(
    `SELECT oi.quantity, oi.delivered_quantity AS deliveredQuantity,
            o.table_id AS tableId, o.table_group_id AS tableGroupId,
            o.external_name AS externalName, o.status AS orderStatus
     FROM order_items oi JOIN orders o ON o.id = oi.order_id
     LEFT JOIN hall_tables t ON t.id = o.table_id
     WHERE oi.id = ? AND o.id = ? AND COALESCE(o.hall_id, t.hall_id) = ?
       AND o.status != 'closed'`,
  ).bind(itemId, orderId, hallId).first<{
    quantity: number; deliveredQuantity: number; tableId: number | null;
    tableGroupId: number | null; externalName: string | null; orderStatus: string;
  }>();
  if (!item || unitIndex >= item.quantity) {
    return c.json({ error: 'ORDER_ITEM_UNIT_NOT_FOUND' }, 404);
  }
  const delivery = await c.env.DB.prepare(
    `SELECT 1 FROM order_item_deliveries
     WHERE order_item_id = ? AND unit_index = ?`,
  ).bind(itemId, unitIndex).first();
  if (deliver === (delivery !== null)) {
    return c.json({ error: deliver
      ? 'ORDER_ITEM_ALREADY_DELIVERED' : 'ORDER_ITEM_NOT_DELIVERED' }, 409);
  }
  const deliveredQuantity = item.deliveredQuantity + (deliver ? 1 : -1);
  const pendingElsewhere = await c.env.DB.prepare(
    `SELECT 1 FROM order_items
     WHERE order_id = ? AND id != ? AND delivered_quantity < quantity LIMIT 1`,
  ).bind(orderId, itemId).first() !== null;
  const nextStatus = !deliver || deliveredQuantity < item.quantity || pendingElsewhere
    ? 'waiting' : 'eating';
  const session = c.get('session');
  const now = new Date().toISOString();
  const label = await tableLabel(c.env.DB, item.tableId, item.tableGroupId, item.externalName);
  await c.env.DB.batch([
    deliver
      ? c.env.DB.prepare(
        `INSERT INTO order_item_deliveries
         (order_item_id, unit_index, delivered_at, delivered_by) VALUES (?, ?, ?, ?)`,
      ).bind(itemId, unitIndex, now, session.user.id)
      : c.env.DB.prepare(
        `DELETE FROM order_item_deliveries WHERE order_item_id = ? AND unit_index = ?`,
      ).bind(itemId, unitIndex),
    c.env.DB.prepare(
      `UPDATE order_items SET delivered_quantity = ?, status = ? WHERE id = ?`,
    ).bind(deliveredQuantity, deliveredQuantity === item.quantity ? 'delivered' : 'ordered', itemId),
    c.env.DB.prepare('UPDATE orders SET status = ?, updated_at = ? WHERE id = ?')
      .bind(nextStatus, now, orderId),
    ...(item.tableId === null ? [] : targetStatusStatements(
      c.env.DB, item.tableId, item.tableGroupId, nextStatus)),
    modification(c.env.DB, orderId, itemId, session.user.id,
      deliver ? 'deliver' : 'undo_delivery', String(item.deliveredQuantity),
      String(deliveredQuantity), now),
    activity(c.env.DB, session.user.id, hallId, 'Pedido', deliver
      ? `${nextStatus === 'eating' ? 'Entregó el último producto' : 'Entregó un producto'} de ${label}`
      : `Deshizo una entrega de ${label}`, now),
    ...statusMetricTransition(c.env.DB, hallId, item.orderStatus, nextStatus),
  ]);
  return c.json({ order: await readOrder(c.env.DB, orderId) });
}

export default routes;
