import { idList, positiveId, rows } from '../../shared/http';

export type OrderItemInput = {
  productId: number;
  quantity: number;
  specifications: string | null;
  removedIngredientIds: number[];
  parentIndex: number | null;
};

export type OrderInput = { description: string | null; items: OrderItemInput[] };

export async function parseOrderInput(database: D1Database,
  body: Record<string, unknown>, hallId: number, allowEmpty = false): Promise<OrderInput | undefined> {
  if (!Array.isArray(body.items) || (!allowEmpty && body.items.length === 0)) return undefined;
  const items: OrderItemInput[] = [];
  for (let index = 0; index < body.items.length; index++) {
    const raw = body.items[index];
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined;
    const value = raw as Record<string, unknown>;
    const productId = positiveId(value.productId);
    const quantity = Number(value.quantity);
    const parentIndex = value.parentIndex == null ? null : Number(value.parentIndex);
    const removedIngredientIds = idList(value.removedIngredientIds);
    if (!productId || !Number.isSafeInteger(quantity) || quantity < 1 || quantity > 99 ||
        !removedIngredientIds || (parentIndex !== null && (!Number.isSafeInteger(parentIndex) ||
          parentIndex < 0 || parentIndex >= index))) return undefined;
    items.push({
      productId, quantity, parentIndex, removedIngredientIds,
      specifications: typeof value.specifications === 'string'
        ? value.specifications.trim().slice(0, 1000) || null : null,
    });
  }
  if (!await validateProducts(database, hallId, items)) return undefined;
  return {
    description: typeof body.description === 'string'
      ? body.description.trim().slice(0, 1000) || null : null,
    items,
  };
}

async function validateProducts(database: D1Database, hallId: number,
  items: OrderItemInput[]) {
  if (items.length === 0) return true;
  const productIds = [...new Set(items.map((item) => item.productId))];
  const placeholders = productIds.map(() => '?').join(',');
  const products = await rows<{ id: number; special: number }>(database.prepare(
    `SELECT p.id, c.is_special AS special FROM products p
     JOIN menu_categories c ON c.id = p.category_id
     JOIN menu_halls mh ON mh.menu_id = p.menu_id AND mh.hall_id = ?
     WHERE p.id IN (${placeholders}) AND p.is_active = 1
       AND (mh.is_primary = 1 OR EXISTS (
         SELECT 1 FROM product_halls ph
         WHERE ph.product_id = p.id AND ph.hall_id = ?
       ))`,
  ).bind(hallId, ...productIds, hallId));
  if (new Set(products.map((row) => row.id)).size !== productIds.length) return false;

  const ingredientPairs = await rows<{ productId: number; ingredientId: number }>(
    database.prepare(
      `SELECT product_id AS productId, ingredient_id AS ingredientId
       FROM product_ingredients WHERE product_id IN (${placeholders})`,
    ).bind(...productIds),
  );
  for (const item of items) {
    if (item.removedIngredientIds.some((id) => !ingredientPairs.some(
      (pair) => pair.productId === item.productId && pair.ingredientId === id,
    ))) return false;
    if (item.parentIndex !== null) {
      const child = products.find((row) => row.id === item.productId);
      const parent = products.find((row) => row.id === items[item.parentIndex!].productId);
      if (child?.special !== 1 || parent?.special !== 0) return false;
    }
  }
  return true;
}
