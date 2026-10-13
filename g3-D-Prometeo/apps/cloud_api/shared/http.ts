import type { Context } from 'hono';
import type { CloudEnvironment } from '../types';

export async function jsonObject(c: Context<CloudEnvironment>): Promise<
  Record<string, unknown> | Response
> {
  let value: unknown;
  try {
    value = await c.req.json<unknown>();
  } catch {
    return c.json({ error: 'INVALID_JSON' }, 400);
  }
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return c.json({ error: 'INVALID_JSON' }, 400);
  }
  return value as Record<string, unknown>;
}

export function positiveId(value: unknown): number | undefined {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

export function idList(value: unknown): number[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const result = value.map(Number);
  return result.every((id) => Number.isSafeInteger(id) && id > 0) &&
    new Set(result).size === result.length ? result : undefined;
}

export function requiredText(value: unknown, min: number, max: number): string | undefined {
  if (typeof value !== 'string') return undefined;
  const result = value.trim();
  return result.length >= min && result.length <= max ? result : undefined;
}

export function optionalText(value: unknown, max: number): string | null | undefined {
  if (value == null || value === '') return null;
  if (typeof value !== 'string') return undefined;
  const result = value.trim();
  return result.length <= max ? result || null : undefined;
}

export async function rows<T>(statement: D1PreparedStatement): Promise<T[]> {
  return (await statement.all<T>()).results;
}

export async function exists(database: D1Database, table: string, id: number) {
  const allowed = new Set([
    'hall', 'menu', 'menu_categories', 'products', 'ingredients',
    'ingredient_categories', 'hall_tables', 'table_groups', 'orders',
  ]);
  if (!allowed.has(table)) throw new Error(`Unsafe table name: ${table}`);
  return await database.prepare(`SELECT 1 FROM ${table} WHERE id = ? LIMIT 1`)
    .bind(id).first() !== null;
}

export async function allExist(database: D1Database, table: string, ids: number[]) {
  if (ids.length === 0) return true;
  const unique = [...new Set(ids)];
  const placeholders = unique.map(() => '?').join(',');
  const allowed = new Set(['hall', 'menu', 'ingredients', 'products']);
  if (!allowed.has(table)) throw new Error(`Unsafe table name: ${table}`);
  const row = await database.prepare(
    `SELECT COUNT(*) AS count FROM ${table} WHERE id IN (${placeholders})`,
  ).bind(...unique).first<{ count: number }>();
  return Number(row?.count ?? 0) === unique.length;
}

// Explicit IDs let every dependent statement participate in one atomic D1 batch.
export function generatedId(): number {
  const parts = new Uint32Array(2);
  crypto.getRandomValues(parts);
  return 1_000_000_000_000 + ((parts[0] & 0xfffff) * 0x100000000) + parts[1];
}

export function isConstraintError(error: unknown): boolean {
  const message = String(error);
  return message.includes('UNIQUE constraint failed') ||
    message.includes('constraint failed');
}
