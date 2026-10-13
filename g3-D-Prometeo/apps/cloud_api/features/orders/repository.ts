import { rows } from '../../shared/http';

type OrderBase = {
  id: number; tableId: number | null; tableGroupId: number | null;
  externalName: string | null; hallId: number; authorId: number;
  description: string | null; status: string; paymentMethod: string | null;
  createdAt: string; updatedAt: string; tableLabel: string;
};

type OrderItemRow = Record<string, unknown> & {
  id: number;
  productId: number;
  unitValue: number;
  quantity: number;
  deliveredQuantity: number;
  specifications: string | null;
  parentOrderItemId: number | null;
  name: string;
  categoryName: string;
  productDescription: string | null;
};

type SplitAccountRow = {
  id: number; name: string; position: number; status: string;
  paymentMethod: string | null; tipPercent: number; tipValue: number;
  subtotal: number; totalValue: number; paidAt: string | null;
};

export async function readOrder(database: D1Database, orderId: number) {
  const order = await database.prepare(
    `SELECT o.id, o.table_id AS tableId, o.table_group_id AS tableGroupId,
            o.external_name AS externalName,
            COALESCE(o.hall_id, t.hall_id) AS hallId,
            o.author_id AS authorId, o.description, o.status,
            o.payment_method AS paymentMethod, o.created_at AS createdAt,
            o.updated_at AS updatedAt,
            COALESCE(o.external_name, g.visible_identifier, t.identifier) AS tableLabel
     FROM orders o
     LEFT JOIN hall_tables t ON t.id = o.table_id
     LEFT JOIN table_groups g ON g.id = o.table_group_id
     WHERE o.id = ?`,
  ).bind(orderId).first<OrderBase>();
  if (!order) return undefined;

  const items = await rows<OrderItemRow>(
    database.prepare(
      `WITH RECURSIVE category_paths(item_id, category_id, parent_id, path) AS (
         SELECT oi.id, category.id, category.parent_category_id, category.name
         FROM order_items oi
         JOIN products product ON product.id = oi.product_id
         JOIN menu_categories category ON category.id = product.category_id
         WHERE oi.order_id = ?
         UNION ALL
         SELECT category_paths.item_id, parent.id, parent.parent_category_id,
                parent.name || ' › ' || category_paths.path
         FROM category_paths
         JOIN menu_categories parent ON parent.id = category_paths.parent_id
       )
       SELECT oi.id, oi.product_id AS productId, p.name,
              category_paths.path AS categoryName,
              p.description AS productDescription,
              COALESCE(oi.unit_value, p.value) AS unitValue,
              oi.quantity, oi.delivered_quantity AS deliveredQuantity,
              oi.specifications, oi.parent_order_item_id AS parentOrderItemId,
              oi.status
       FROM order_items oi
       JOIN products p ON p.id = oi.product_id
       JOIN category_paths
         ON category_paths.item_id = oi.id AND category_paths.parent_id IS NULL
       WHERE oi.order_id = ? ORDER BY oi.id`,
    ).bind(orderId, orderId),
  );
  const ids = items.map((item) => item.id);
  const productIds = [...new Set(items.map((item) => item.productId))];
  const placeholders = ids.map(() => '?').join(',');
  const productPlaceholders = productIds.map(() => '?').join(',');
  const [deliveries, removedIngredients, ingredients, removedItems,
    splitAccounts, splitAllocations] = await Promise.all([
    ids.length === 0 ? [] : rows<{ itemId: number; unitIndex: number }>(database.prepare(
      `SELECT order_item_id AS itemId, unit_index AS unitIndex
       FROM order_item_deliveries WHERE order_item_id IN (${placeholders})
       ORDER BY order_item_id, unit_index`,
    ).bind(...ids)),
    ids.length === 0 ? [] : rows<{ itemId: number; ingredientId: number }>(database.prepare(
      `SELECT order_item_id AS itemId, ingredient_id AS ingredientId
       FROM order_item_removed_ingredients WHERE order_item_id IN (${placeholders})
       ORDER BY order_item_id, ingredient_id`,
    ).bind(...ids)),
    productIds.length === 0 ? [] : rows<{ productId: number; id: number; name: string }>(
      database.prepare(
        `SELECT pi.product_id AS productId, i.id, i.name
         FROM product_ingredients pi JOIN ingredients i ON i.id = pi.ingredient_id
         WHERE pi.product_id IN (${productPlaceholders})
         ORDER BY pi.product_id, i.name COLLATE NOCASE`,
      ).bind(...productIds),
    ),
    rows(database.prepare(
      `SELECT id, product_name AS name, category_name AS categoryName,
              product_description AS productDescription, unit_value AS unitValue,
              quantity, specifications, parent_product_name AS parentProductName,
              removed_at AS removedAt
       FROM removed_order_items WHERE order_id = ? ORDER BY id`,
    ).bind(orderId)),
    rows<SplitAccountRow>(database.prepare(
      `SELECT id, name, position, status, payment_method AS paymentMethod,
              tip_percent AS tipPercent, tip_value AS tipValue,
              subtotal, total_value AS totalValue, paid_at AS paidAt
       FROM order_split_accounts WHERE order_id = ? ORDER BY position, id`,
    ).bind(orderId)),
    rows<{ accountId: number; orderItemId: number; halfUnits: number }>(database.prepare(
      `SELECT allocation.account_id AS accountId,
              allocation.order_item_id AS orderItemId,
              allocation.half_units AS halfUnits
       FROM order_split_allocations allocation
       JOIN order_split_accounts account ON account.id = allocation.account_id
       WHERE account.order_id = ? ORDER BY account.position, allocation.order_item_id`,
    ).bind(orderId)),
  ]);
  const enriched = items.map((item) => ({
    ...item,
    deliveredUnitIndexes: deliveries.filter((row) => row.itemId === item.id)
      .map((row) => row.unitIndex),
    removedIngredientIds: removedIngredients.filter((row) => row.itemId === item.id)
      .map((row) => row.ingredientId),
    ingredients: ingredients.filter((row) => row.productId === item.productId)
      .map(({ id, name }) => ({ id, name })),
  }));
  return {
    ...order,
    total: enriched.reduce(
      (sum, item) => sum + Number(item.unitValue) * Number(item.quantity), 0,
    ),
    removedItems,
    splitAccounts: splitAccounts.map((account) => ({
      ...account,
      allocations: splitAllocations
        .filter((allocation) => allocation.accountId === account.id)
        .map(({ orderItemId, halfUnits }) => ({ orderItemId, halfUnits })),
    })),
    items: enriched,
  };
}

export async function readHallOrders(database: D1Database, hallId: number,
  closed = false, from?: string, to?: string, paymentMethod?: string) {
  const clauses = ['COALESCE(o.hall_id, t.hall_id) = ?'];
  const bindings: Array<string | number> = [hallId];
  if (closed) {
    const paymentClauses = ["payment.order_id = o.id", "payment.status = 'paid'"];
    if (from && to) {
      paymentClauses.push('payment.paid_at >= ?', 'payment.paid_at < ?');
      bindings.push(from, to);
    }
    if (paymentMethod) {
      paymentClauses.push('payment.payment_method = ?');
      bindings.push(paymentMethod);
    }
    clauses.push(`EXISTS (
      SELECT 1 FROM order_split_accounts payment
      WHERE ${paymentClauses.join(' AND ')}
    )`);
  } else {
    clauses.push("o.status != 'closed'");
  }
  const ids = await rows<{ id: number }>(database.prepare(
    `SELECT o.id FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
     WHERE ${clauses.join(' AND ')}
     ORDER BY ${closed ? `(SELECT MAX(paid_at) FROM order_split_accounts
       WHERE order_id = o.id AND status = 'paid') DESC` : 'o.created_at, o.id'}`,
  ).bind(...bindings));
  return Promise.all(ids.map((row) => readOrder(database, row.id)));
}

export async function tableLabel(database: D1Database, tableId: number | null,
  groupId: number | null, externalName?: string | null) {
  if (externalName) return externalName;
  if (groupId !== null) {
    const group = await database.prepare(
      'SELECT visible_identifier AS label FROM table_groups WHERE id = ?',
    ).bind(groupId).first<{ label: string }>();
    if (group?.label) return group.label;
  }
  const table = tableId === null ? null : await database.prepare(
    'SELECT identifier AS label FROM hall_tables WHERE id = ?',
  ).bind(tableId).first<{ label: string }>();
  return table?.label ?? String(tableId ?? 'Pedido externo');
}
