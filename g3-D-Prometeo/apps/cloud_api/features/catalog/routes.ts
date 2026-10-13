import { Hono } from 'hono';
import { requireAdmin, requireSession } from '../auth/session';
import { readCatalog } from '../read_model/repository';
import {
  allExist, exists, generatedId, idList, isConstraintError, jsonObject,
  optionalText, positiveId, requiredText, rows,
} from '../../shared/http';
import type { CloudEnvironment } from '../../types';

const routes = new Hono<CloudEnvironment>();
routes.use('*', requireSession, requireAdmin);

routes.put('/halls/:hallId/menus', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  if (!hallId || !await exists(c.env.DB, 'hall', hallId)) {
    return c.json({ error: 'HALL_NOT_FOUND' }, 404);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const primaryMenuId = positiveId(body.primaryMenuId);
  const secondary = parseSecondaryMenus(body.secondaryMenus);
  if (!primaryMenuId || !secondary || secondary.some((item) => item.menuId === primaryMenuId) ||
      !await allExist(c.env.DB, 'menu', [primaryMenuId, ...secondary.map((item) => item.menuId)])) {
    return c.json({ error: 'INVALID_MENU_ASSIGNMENTS' }, 422);
  }
  for (const item of secondary) {
    if (!await productsBelongToMenu(c.env.DB, item.menuId, item.productIds)) {
      return c.json({ error: 'INVALID_MENU_ASSIGNMENTS' }, 422);
    }
  }
  const statements: D1PreparedStatement[] = [
    c.env.DB.prepare('DELETE FROM product_halls WHERE hall_id = ?').bind(hallId),
    c.env.DB.prepare('DELETE FROM menu_halls WHERE hall_id = ?').bind(hallId),
    c.env.DB.prepare(
      'INSERT INTO menu_halls (menu_id, hall_id, is_primary) VALUES (?, ?, 1)',
    ).bind(primaryMenuId, hallId),
  ];
  for (const item of secondary) {
    statements.push(c.env.DB.prepare(
      'INSERT INTO menu_halls (menu_id, hall_id, is_primary) VALUES (?, ?, 0)',
    ).bind(item.menuId, hallId));
    statements.push(...item.productIds.map((productId) => c.env.DB.prepare(
      'INSERT INTO product_halls (product_id, hall_id) VALUES (?, ?)',
    ).bind(productId, hallId)));
  }
  await c.env.DB.batch(statements);
  return c.json({ primaryMenuId, secondaryMenus: secondary });
});

routes.post('/ingredient-categories', async (c) => {
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = requiredText(body.name, 2, 80);
  if (!name) return c.json({ error: 'INVALID_INGREDIENT_CATEGORY' }, 422);
  const id = generatedId();
  try {
    await c.env.DB.prepare(
      'INSERT INTO ingredient_categories (id, name) VALUES (?, ?)',
    ).bind(id, name).run();
  } catch (error) {
    if (isConstraintError(error)) {
      return c.json({ error: 'INGREDIENT_CATEGORY_NAME_TAKEN' }, 409);
    }
    throw error;
  }
  return c.json({ category: { id, name, ingredients: [] } }, 201);
});

routes.post('/ingredients', async (c) => {
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = requiredText(body.name, 2, 80);
  const description = optionalText(body.description, 500);
  const categoryId = positiveId(body.categoryId);
  if (!name || description === undefined || !categoryId ||
      !await exists(c.env.DB, 'ingredient_categories', categoryId)) {
    return c.json({ error: 'INVALID_INGREDIENT' }, 422);
  }
  const id = generatedId();
  try {
    await c.env.DB.prepare(
      `INSERT INTO ingredients (id, name, description, category_id)
       VALUES (?, ?, ?, ?)`,
    ).bind(id, name, description, categoryId).run();
  } catch (error) {
    if (isConstraintError(error)) return c.json({ error: 'INGREDIENT_NAME_TAKEN' }, 409);
    throw error;
  }
  return c.json({ ingredient: { id, name, description, categoryId } }, 201);
});

routes.post('/menus', async (c) => {
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = requiredText(body.name, 2, 80);
  const assignments = parseAssignments(body.hallAssignments);
  if (!name || !assignments ||
      !await allExist(c.env.DB, 'hall', assignments.map((item) => item.hallId))) {
    return c.json({ error: 'INVALID_MENU' }, 422);
  }
  const id = generatedId();
  const statements = [
    c.env.DB.prepare('INSERT INTO menu (id, name) VALUES (?, ?)').bind(id, name),
    ...assignments.map((item) => c.env.DB.prepare(
      'INSERT INTO menu_halls (menu_id, hall_id, is_primary) VALUES (?, ?, ?)',
    ).bind(id, item.hallId, item.isPrimary ? 1 : 0)),
  ];
  try {
    await c.env.DB.batch(statements);
  } catch (error) {
    if (isConstraintError(error)) {
      return c.json({
        error: String(error).includes('menu_halls.hall_id')
          ? 'PRIMARY_MENU_TAKEN' : 'MENU_NAME_TAKEN',
      }, 409);
    }
    throw error;
  }
  const catalog = await readCatalog(c.env.DB, id);
  return c.json({ menu: catalog.menus[0] }, 201);
});

routes.post('/menus/:menuId/categories', async (c) => {
  const menuId = positiveId(c.req.param('menuId'));
  if (!menuId || !await exists(c.env.DB, 'menu', menuId)) {
    return c.json({ error: 'MENU_NOT_FOUND' }, 404);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = requiredText(body.name, 2, 60);
  const parentCategoryId = body.parentCategoryId == null
    ? null : positiveId(body.parentCategoryId);
  if (!name || (body.parentCategoryId != null && !parentCategoryId)) {
    return c.json({ error: 'INVALID_CATEGORY' }, 422);
  }
  let isSpecial = body.isSpecial === true;
  if (parentCategoryId !== null) {
    const parent = await c.env.DB.prepare(
      `SELECT is_special AS isSpecial FROM menu_categories
       WHERE id = ? AND menu_id = ?`,
    ).bind(parentCategoryId, menuId).first<{ isSpecial: number }>();
    if (!parent) return c.json({ error: 'INVALID_PARENT_CATEGORY' }, 422);
    isSpecial = parent.isSpecial === 1;
  }
  const position = Number((await c.env.DB.prepare(
    `SELECT COALESCE(MAX(position) + 1, 0) AS position FROM menu_categories
     WHERE menu_id = ? AND parent_category_id IS ?`,
  ).bind(menuId, parentCategoryId).first<{ position: number }>())?.position ?? 0);
  const id = generatedId();
  await c.env.DB.prepare(
    `INSERT INTO menu_categories
     (id, menu_id, name, parent_category_id, is_special, position)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(id, menuId, name, parentCategoryId, isSpecial ? 1 : 0, position).run();
  return c.json({ category: {
    id, menuId, name, parentCategoryId, isSpecial, products: [], subcategories: [],
  } }, 201);
});

routes.put('/menus/:menuId/category-order', async (c) => {
  const menuId = positiveId(c.req.param('menuId'));
  if (!menuId || !await exists(c.env.DB, 'menu', menuId)) {
    return c.json({ error: 'MENU_NOT_FOUND' }, 404);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const parentCategoryId = body.parentCategoryId == null
    ? null : positiveId(body.parentCategoryId);
  const categoryIds = idList(body.categoryIds);
  if ((body.parentCategoryId != null && !parentCategoryId) || !categoryIds) {
    return c.json({ error: 'INVALID_CATEGORY_ORDER' }, 422);
  }
  const current = (await rows<{ id: number }>(c.env.DB.prepare(
    `SELECT id FROM menu_categories
     WHERE menu_id = ? AND parent_category_id IS ?`,
  ).bind(menuId, parentCategoryId))).map((row) => row.id);
  if (!sameIds(current, categoryIds)) {
    return c.json({ error: 'INVALID_CATEGORY_ORDER' }, 422);
  }
  await c.env.DB.batch(categoryIds.map((id, position) => c.env.DB.prepare(
    'UPDATE menu_categories SET position = ? WHERE id = ? AND menu_id = ?',
  ).bind(position, id, menuId)));
  return c.json({ categoryIds });
});

routes.patch('/categories/:categoryId', async (c) => {
  const categoryId = positiveId(c.req.param('categoryId'));
  const current = categoryId ? await c.env.DB.prepare(
    'SELECT menu_id AS menuId FROM menu_categories WHERE id = ?',
  ).bind(categoryId).first<{ menuId: number }>() : null;
  if (!categoryId || !current) return c.json({ error: 'CATEGORY_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const name = requiredText(body.name, 2, 60);
  if (!name) return c.json({ error: 'INVALID_CATEGORY' }, 422);
  await c.env.DB.prepare('UPDATE menu_categories SET name = ? WHERE id = ?')
    .bind(name, categoryId).run();
  return c.json({ category: { id: categoryId, menuId: current.menuId, name } });
});

routes.post('/menus/:menuId/products', async (c) => {
  const menuId = positiveId(c.req.param('menuId'));
  if (!menuId || !await exists(c.env.DB, 'menu', menuId)) {
    return c.json({ error: 'MENU_NOT_FOUND' }, 404);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const parsed = await parseProduct(c.env.DB, body, menuId);
  if (!parsed) return c.json({ error: 'INVALID_PRODUCT' }, 422);
  const id = generatedId();
  const position = Number((await c.env.DB.prepare(
    `SELECT COALESCE(MAX(position) + 1, 0) AS position
     FROM category_product_positions WHERE category_id = ?`,
  ).bind(parsed.categoryId).first<{ position: number }>())?.position ?? 0);
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO products
       (id, name, description, value, menu_id, category_id)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).bind(id, parsed.name, parsed.description, parsed.value, menuId, parsed.categoryId),
    c.env.DB.prepare(
      `INSERT INTO category_product_positions
       (category_id, product_id, position) VALUES (?, ?, ?)`,
    ).bind(parsed.categoryId, id, position),
    ...parsed.ingredientIds.map((ingredientId) => c.env.DB.prepare(
      'INSERT INTO product_ingredients (product_id, ingredient_id) VALUES (?, ?)',
    ).bind(id, ingredientId)),
    ...parsed.hallIds.map((hallId) => c.env.DB.prepare(
      'INSERT INTO product_halls (product_id, hall_id) VALUES (?, ?)',
    ).bind(id, hallId)),
  ]);
  return c.json({ product: await readProduct(c.env.DB, id) }, 201);
});

routes.patch('/products/:productId', async (c) => {
  const productId = positiveId(c.req.param('productId'));
  const current = productId ? await c.env.DB.prepare(
    `SELECT menu_id AS menuId, category_id AS categoryId FROM products
     WHERE id = ? AND is_active = 1`,
  ).bind(productId).first<{ menuId: number; categoryId: number }>() : null;
  if (!productId || !current) return c.json({ error: 'PRODUCT_NOT_FOUND' }, 404);
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const parsed = await parseProduct(c.env.DB, body, current.menuId, current.categoryId);
  if (!parsed) return c.json({ error: 'INVALID_PRODUCT' }, 422);
  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE products SET name = ?, description = ?, value = ? WHERE id = ?`,
    ).bind(parsed.name, parsed.description, parsed.value, productId),
    c.env.DB.prepare('DELETE FROM product_ingredients WHERE product_id = ?').bind(productId),
    c.env.DB.prepare('DELETE FROM product_halls WHERE product_id = ?').bind(productId),
    ...parsed.ingredientIds.map((id) => c.env.DB.prepare(
      'INSERT INTO product_ingredients (product_id, ingredient_id) VALUES (?, ?)',
    ).bind(productId, id)),
    ...parsed.hallIds.map((id) => c.env.DB.prepare(
      'INSERT INTO product_halls (product_id, hall_id) VALUES (?, ?)',
    ).bind(productId, id)),
  ]);
  return c.json({ product: await readProduct(c.env.DB, productId) });
});

routes.put('/categories/:categoryId/product-order', async (c) => {
  const categoryId = positiveId(c.req.param('categoryId'));
  if (!categoryId || !await exists(c.env.DB, 'menu_categories', categoryId)) {
    return c.json({ error: 'CATEGORY_NOT_FOUND' }, 404);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const productIds = idList(body.productIds);
  const current = (await rows<{ id: number }>(c.env.DB.prepare(
    'SELECT id FROM products WHERE category_id = ? AND is_active = 1',
  ).bind(categoryId))).map((row) => row.id);
  if (!productIds || !sameIds(current, productIds)) {
    return c.json({ error: 'INVALID_PRODUCT_ORDER' }, 422);
  }
  await c.env.DB.batch([
    c.env.DB.prepare(
      'DELETE FROM category_product_positions WHERE category_id = ?',
    ).bind(categoryId),
    ...productIds.map((id, position) => c.env.DB.prepare(
      `INSERT INTO category_product_positions
       (category_id, product_id, position) VALUES (?, ?, ?)`,
    ).bind(categoryId, id, position)),
  ]);
  return c.json({ productIds });
});

routes.delete('/products/:productId', async (c) => {
  const productId = positiveId(c.req.param('productId'));
  if (!productId) return c.json({ error: 'PRODUCT_NOT_FOUND' }, 404);
  const result = await c.env.DB.prepare(
    'UPDATE products SET is_active = 0 WHERE id = ? AND is_active = 1',
  ).bind(productId).run();
  return result.meta.changes === 0
    ? c.json({ error: 'PRODUCT_NOT_FOUND' }, 404)
    : c.body(null, 204);
});

type ProductInput = {
  name: string; description: string | null; value: number; categoryId: number;
  ingredientIds: number[]; hallIds: number[];
};

async function parseProduct(database: D1Database, body: Record<string, unknown>,
  menuId: number, fixedCategoryId?: number): Promise<ProductInput | undefined> {
  const name = requiredText(body.name, 2, 100);
  const description = optionalText(body.description, 1000);
  const value = Number(body.value);
  const ingredientIds = idList(body.ingredientIds);
  const hallIds = idList(body.hallIds);
  const resolvedCategoryId = fixedCategoryId ?? positiveId(body.categoryId);
  if (!name || description === undefined || !Number.isSafeInteger(value) || value < 0 ||
      !resolvedCategoryId || !ingredientIds || !hallIds ||
      !await allExist(database, 'ingredients', ingredientIds)) return undefined;
  const category = await database.prepare(
    'SELECT 1 FROM menu_categories WHERE id = ? AND menu_id = ?',
  ).bind(resolvedCategoryId, menuId).first();
  if (!category) return undefined;
  const secondaryHalls = (await rows<{ id: number }>(database.prepare(
    `SELECT hall_id AS id FROM menu_halls
     WHERE menu_id = ? AND is_primary = 0`,
  ).bind(menuId))).map((row) => row.id);
  if (hallIds.some((id) => !secondaryHalls.includes(id))) return undefined;
  return { name, description, value, categoryId: resolvedCategoryId, ingredientIds, hallIds };
}

async function readProduct(database: D1Database, id: number) {
  const product = await database.prepare(
    `SELECT id, name, description, value, menu_id AS menuId,
            category_id AS categoryId, is_active AS isActive
     FROM products WHERE id = ?`,
  ).bind(id).first<Record<string, unknown>>();
  const [ingredients, halls] = await Promise.all([
    rows<{ id: number }>(database.prepare(
      'SELECT ingredient_id AS id FROM product_ingredients WHERE product_id = ?',
    ).bind(id)),
    rows<{ id: number }>(database.prepare(
      'SELECT hall_id AS id FROM product_halls WHERE product_id = ?',
    ).bind(id)),
  ]);
  return { ...product, isActive: product?.isActive === 1,
    ingredientIds: ingredients.map((row) => row.id), hallIds: halls.map((row) => row.id) };
}

function parseAssignments(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const result: Array<{ hallId: number; isPrimary: boolean }> = [];
  for (const raw of value) {
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined;
    const item = raw as Record<string, unknown>;
    const hallId = positiveId(item.hallId);
    if (!hallId || typeof item.isPrimary !== 'boolean') return undefined;
    result.push({ hallId, isPrimary: item.isPrimary });
  }
  if (new Set(result.map((item) => item.hallId)).size !== result.length) return undefined;
  return result;
}

function parseSecondaryMenus(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const result: Array<{ menuId: number; productIds: number[] }> = [];
  for (const raw of value) {
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return undefined;
    const item = raw as Record<string, unknown>;
    const menuId = positiveId(item.menuId);
    const productIds = idList(item.productIds);
    if (!menuId || !productIds) return undefined;
    result.push({ menuId, productIds });
  }
  return new Set(result.map((item) => item.menuId)).size === result.length ? result : undefined;
}

async function productsBelongToMenu(database: D1Database, menuId: number, ids: number[]) {
  if (ids.length === 0) return true;
  const placeholders = ids.map(() => '?').join(',');
  const row = await database.prepare(
    `SELECT COUNT(*) AS count FROM products
     WHERE menu_id = ? AND is_active = 1 AND id IN (${placeholders})`,
  ).bind(menuId, ...ids).first<{ count: number }>();
  return Number(row?.count ?? 0) === ids.length;
}

function sameIds(left: number[], right: number[]) {
  return left.length === right.length && left.every((id) => right.includes(id));
}

export default routes;
