import { canAccessHall } from '../auth/session';
import { generatedId, positiveId } from '../../shared/http';
import type { CloudEnvironment } from '../../types';
import type { OrderItemInput } from './validation';

export async function accessibleHall(c: import('hono').Context<CloudEnvironment>) {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await canAccessHall(c.env.DB, c.get('session'), hallId)) {
    return c.json({ error: 'HALL_NOT_ASSIGNED' }, 403);
  }
  return hallId;
}

export async function tableTarget(database: D1Database, hallId: number, tableId: number) {
  return database.prepare(
    `SELECT t.status, m.group_id AS groupId
     FROM hall_tables t LEFT JOIN table_group_members m ON m.table_id = t.id
     WHERE t.id = ? AND t.hall_id = ?`,
  ).bind(tableId, hallId).first<{ status: string; groupId: number | null }>();
}

export async function activeOrderAt(database: D1Database, tableId: number,
  groupId: number | null, excludedId = -1) {
  return await database.prepare(
    `SELECT 1 FROM orders WHERE status != 'closed' AND id != ?
     AND (table_id = ? OR (? IS NOT NULL AND table_group_id = ?)) LIMIT 1`,
  ).bind(excludedId, tableId, groupId, groupId).first() !== null;
}

export async function activeOrder(database: D1Database, hallId: number, orderId: number) {
  return database.prepare(
    `SELECT o.table_id AS tableId, o.table_group_id AS tableGroupId,
            o.external_name AS externalName, o.status
     FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
     WHERE o.id = ? AND COALESCE(o.hall_id, t.hall_id) = ? AND o.status != 'closed'`,
  ).bind(orderId, hallId).first<{
    tableId: number | null; tableGroupId: number | null;
    externalName: string | null; status: string;
  }>();
}

export function itemStatements(database: D1Database, orderId: number, items: OrderItemInput[]) {
  const ids = items.map(() => generatedId());
  const statements: D1PreparedStatement[] = [];
  items.forEach((item, index) => {
    statements.push(database.prepare(
      `INSERT INTO order_items
       (id, order_id, product_id, quantity, delivered_quantity, specifications,
        parent_order_item_id, status, unit_value)
       SELECT ?, ?, ?, ?, 0, ?, ?, 'ordered', value FROM products WHERE id = ?`,
    ).bind(ids[index], orderId, item.productId, item.quantity, item.specifications,
      item.parentIndex === null ? null : ids[item.parentIndex], item.productId));
    statements.push(...item.removedIngredientIds.map((ingredientId) => database.prepare(
      `INSERT INTO order_item_removed_ingredients (order_item_id, ingredient_id)
       VALUES (?, ?)`,
    ).bind(ids[index], ingredientId)));
  });
  return statements;
}

export function targetStatusStatements(database: D1Database, tableId: number,
  groupId: number | null, status: string) {
  if (groupId === null) return [database.prepare(
    'UPDATE hall_tables SET status = ? WHERE id = ?',
  ).bind(status, tableId)];
  return [
    database.prepare('UPDATE table_groups SET status = ? WHERE id = ?').bind(status, groupId),
    database.prepare(
      `UPDATE hall_tables SET status = ? WHERE id IN (
         SELECT table_id FROM table_group_members WHERE group_id = ?
       )`,
    ).bind(status, groupId),
  ];
}

export function modification(database: D1Database, orderId: number, itemId: number | null,
  userId: number, type: string, oldValue: string | null, newValue: string | null, now: string) {
  return database.prepare(
    `INSERT INTO order_modifications
     (id, order_id, order_item_id, modifier_id, modification_type,
      old_value, new_value, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(generatedId(), orderId, itemId, userId, type, oldValue, newValue, now);
}

export function activity(database: D1Database, userId: number, hallId: number,
  type: string, message: string, now: string) {
  return database.prepare(
    `INSERT INTO activity_log
     (id, author_id, hall_id, type, modification, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(generatedId(), userId, hallId, type, message, now);
}

export function refreshOrderTotal(database: D1Database, orderId: number) {
  return database.prepare(
    `UPDATE orders SET total_value = COALESCE((
       SELECT SUM(unit_value * quantity) FROM order_items WHERE order_id = ?
     ), 0) WHERE id = ?`,
  ).bind(orderId, orderId);
}

export async function hasPendingItems(database: D1Database, orderId: number) {
  return await database.prepare(
    `SELECT 1 FROM order_items
     WHERE order_id = ? AND delivered_quantity < quantity LIMIT 1`,
  ).bind(orderId).first() !== null;
}

export type StoredItem = Record<string, unknown> & {
  id: number; productId: number; quantity: number; deliveredQuantity: number;
  specifications: string | null; parentOrderItemId: number | null;
  removedIngredientIds: number[]; unitValue: number; name: string;
  categoryName: string; productDescription: string | null;
};

export function signature(productId: number, specifications: string | null,
  removed: number[], parentProductId: number | null) {
  return JSON.stringify([productId, specifications ?? '', [...removed].sort((a, b) => a - b),
    parentProductId]);
}

export function requestedCounts(items: OrderItemInput[]) {
  const counts = new Map<string, number>();
  items.forEach((item) => {
    const parentProductId = item.parentIndex === null ? null : items[item.parentIndex].productId;
    const key = signature(item.productId, item.specifications,
      item.removedIngredientIds, parentProductId);
    counts.set(key, (counts.get(key) ?? 0) + item.quantity);
  });
  return counts;
}

export function keepsDelivered(stored: StoredItem[], requested: OrderItemInput[]) {
  const counts = requestedCounts(requested);
  const byId = new Map(stored.map((item) => [item.id, item]));
  const delivered = new Map<string, number>();
  stored.forEach((item) => {
    const parent = item.parentOrderItemId === null ? null : byId.get(item.parentOrderItemId);
    const key = signature(item.productId, item.specifications, item.removedIngredientIds,
      parent?.productId ?? null);
    delivered.set(key, (delivered.get(key) ?? 0) + item.deliveredQuantity);
  });
  return [...delivered].every(([key, count]) => (counts.get(key) ?? 0) >= count);
}

export function buildReplacement(database: D1Database, orderId: number,
  stored: StoredItem[], requested: OrderItemInput[], now: string) {
  const byId = new Map(stored.map((item) => [item.id, item]));
  const delivered = new Map<string, number>();
  stored.forEach((item) => {
    const parent = item.parentOrderItemId === null ? null : byId.get(item.parentOrderItemId);
    const key = signature(item.productId, item.specifications, item.removedIngredientIds,
      parent?.productId ?? null);
    delivered.set(key, (delivered.get(key) ?? 0) + item.deliveredQuantity);
  });
  const ids = requested.map(() => generatedId());
  const remaining = requestedCounts(requested);
  const statements: D1PreparedStatement[] = [
    database.prepare(
      `UPDATE order_modifications SET order_item_id = NULL
       WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id = ?)`,
    ).bind(orderId),
    database.prepare('DELETE FROM order_items WHERE order_id = ?').bind(orderId),
  ];
  for (const item of stored) {
    const parent = item.parentOrderItemId === null ? null : byId.get(item.parentOrderItemId);
    const key = signature(item.productId, item.specifications, item.removedIngredientIds,
      parent?.productId ?? null);
    const retained = Math.min(remaining.get(key) ?? 0, item.quantity);
    remaining.set(key, Math.max(0, (remaining.get(key) ?? 0) - retained));
    const removedQuantity = item.quantity - retained;
    if (removedQuantity > 0) {
      statements.unshift(database.prepare(
        `INSERT INTO removed_order_items
         (id, order_id, product_name, category_name, product_description,
          unit_value, quantity, specifications, parent_product_name, removed_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(generatedId(), orderId, item.name, item.categoryName,
        item.productDescription, item.unitValue, removedQuantity, item.specifications,
        parent?.name ?? null, now));
    }
  }
  const priceQueues = new Map<string, number[]>();
  for (const item of stored) {
    const parent = item.parentOrderItemId === null ? null : byId.get(item.parentOrderItemId);
    const key = signature(item.productId, item.specifications, item.removedIngredientIds,
      parent?.productId ?? null);
    const queue = priceQueues.get(key) ?? [];
    for (let unit = 0; unit < item.quantity; unit++) queue.push(item.unitValue);
    priceQueues.set(key, queue);
  }
  let pending = false;
  requested.forEach((item, index) => {
    const parentProductId = item.parentIndex === null ? null : requested[item.parentIndex].productId;
    const key = signature(item.productId, item.specifications,
      item.removedIngredientIds, parentProductId);
    const deliveredQuantity = Math.min(delivered.get(key) ?? 0, item.quantity);
    delivered.set(key, Math.max(0, (delivered.get(key) ?? 0) - deliveredQuantity));
    pending ||= deliveredQuantity < item.quantity;
    statements.push(database.prepare(
      `INSERT INTO order_items
       (id, order_id, product_id, quantity, delivered_quantity, specifications,
        parent_order_item_id, status, unit_value)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?,
         (SELECT value FROM products WHERE id = ?)))`,
    ).bind(ids[index], orderId, item.productId, item.quantity, deliveredQuantity,
      item.specifications, item.parentIndex === null ? null : ids[item.parentIndex],
      deliveredQuantity === item.quantity ? 'delivered' : 'ordered',
      priceQueues.get(key)?.shift() ?? null, item.productId));
    for (let unit = 0; unit < deliveredQuantity; unit++) {
      statements.push(database.prepare(
        `INSERT INTO order_item_deliveries
         (order_item_id, unit_index, delivered_at, delivered_by)
         VALUES (?, ?, ?, NULL)`,
      ).bind(ids[index], unit, now));
    }
    statements.push(...item.removedIngredientIds.map((ingredientId) => database.prepare(
      `INSERT INTO order_item_removed_ingredients (order_item_id, ingredient_id)
       VALUES (?, ?)`,
    ).bind(ids[index], ingredientId)));
  });
  return { statements, pending };
}

export function bogotaBusinessDay(now: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  const date = `${value('year')}-${value('month')}-${value('day')}`;
  return {
    from: new Date(`${date}T01:00:00-05:00`).toISOString(),
    to: new Date(`${date}T23:59:59.999-05:00`).toISOString(),
  };
}

export type SplitAccountInput = {
  name: string;
  position: number;
  tipPercent: number;
  customTip: number;
  allocations: Array<{ orderItemId: number; halfUnits: number }>;
};

export function parseSplitAccounts(value: unknown): SplitAccountInput[] | undefined {
  if (!Array.isArray(value) || value.length === 0 || value.length > 12) return undefined;
  const result: SplitAccountInput[] = [];
  const positions = new Set<number>();
  for (const raw of value) {
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined;
    const data = raw as Record<string, unknown>;
    const name = typeof data.name === 'string' ? data.name.trim().slice(0, 80) : '';
    const position = Number(data.position);
    const tipPercent = Number(data.tipPercent ?? 0);
    const customTip = Number(data.customTip ?? 0);
    if (!name || !Number.isSafeInteger(position) || position < 0 ||
        positions.has(position) || !Number.isSafeInteger(tipPercent) ||
        tipPercent < 0 || tipPercent > 100 || !Number.isSafeInteger(customTip) ||
        customTip < 0 || !Array.isArray(data.allocations)) return undefined;
    positions.add(position);
    const allocations: SplitAccountInput['allocations'] = [];
    const itemIds = new Set<number>();
    for (const rawAllocation of data.allocations) {
      if (typeof rawAllocation !== 'object' || rawAllocation === null ||
          Array.isArray(rawAllocation)) return undefined;
      const allocation = rawAllocation as Record<string, unknown>;
      const orderItemId = positiveId(allocation.orderItemId);
      const halfUnits = Number(allocation.halfUnits);
      if (!orderItemId || itemIds.has(orderItemId) ||
          !Number.isSafeInteger(halfUnits) || halfUnits < 1) return undefined;
      itemIds.add(orderItemId);
      allocations.push({ orderItemId, halfUnits });
    }
    result.push({ name, position, tipPercent, customTip, allocations });
  }
  return result.sort((left, right) => left.position - right.position);
}

export const paymentMethods = new Set(['cash', 'transfer', 'card']);
