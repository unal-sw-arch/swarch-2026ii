import { Hono } from 'hono';
import { canAccessHall, requireSession } from '../auth/session';
import { readLayout } from '../read_model/repository';
import { generatedId, jsonObject, positiveId, rows } from '../../shared/http';
import type { CloudEnvironment } from '../../types';

type Position = { id: number; x: number; y: number };
type GroupInput = { id: number; identifier: string; tableIds: number[] };

const routes = new Hono<CloudEnvironment>();
routes.use('*', requireSession);

routes.put('/halls/:hallId/live-layout', async (c) => {
  const hallId = positiveId(c.req.param('hallId'));
  const session = c.get('session');
  if (!hallId || !await canAccessHall(c.env.DB, session, hallId)) {
    return c.json({ error: 'HALL_NOT_ASSIGNED' }, 403);
  }
  const body = await jsonObject(c);
  if (body instanceof Response) return body;
  const parsed = await parseLiveLayout(c.env.DB, hallId, body);
  if (!parsed) return c.json({ error: 'INVALID_LAYOUT' }, 422);

  const active = await rows<{ tableId: number | null; groupId: number | null }>(
    c.env.DB.prepare(
      `SELECT table_id AS tableId, table_group_id AS groupId FROM orders
       WHERE status != 'closed' AND (hall_id = ? OR table_id IN (
         SELECT id FROM hall_tables WHERE hall_id = ?
       ))`,
    ).bind(hallId, hallId),
  );
  const groupsBefore = await currentGroups(c.env.DB, hallId);
  if (!preservesActiveTargets(parsed.groups, groupsBefore, active)) {
    return c.json({ error: 'TABLE_HAS_ACTIVE_ORDER' }, 409);
  }

  const existingGroupIds = new Set(groupsBefore.map((group) => group.id));
  const resolvedGroups = parsed.groups.map((group) => ({
    ...group,
    resolvedId: group.id > 0 && existingGroupIds.has(group.id)
      ? group.id : generatedId(),
  }));
  const statements: D1PreparedStatement[] = [
    ...parsed.tables.map((table) => c.env.DB.prepare(
      'UPDATE hall_tables SET x = ?, y = ? WHERE id = ? AND hall_id = ?',
    ).bind(table.x, table.y, table.id, hallId)),
    c.env.DB.prepare(
      `DELETE FROM table_group_members WHERE group_id IN (
         SELECT id FROM table_groups WHERE hall_id = ?
       )`,
    ).bind(hallId),
  ];
  for (const group of resolvedGroups) {
    if (existingGroupIds.has(group.resolvedId)) {
      statements.push(c.env.DB.prepare(
        'UPDATE table_groups SET visible_identifier = ? WHERE id = ? AND hall_id = ?',
      ).bind(group.identifier, group.resolvedId, hallId));
    } else {
      statements.push(c.env.DB.prepare(
        `INSERT INTO table_groups
         (id, hall_id, visible_identifier, status, created_at)
         VALUES (?, ?, ?, 'available', ?)`,
      ).bind(group.resolvedId, hallId, group.identifier, new Date().toISOString()));
    }
    statements.push(...group.tableIds.map((tableId) => c.env.DB.prepare(
      'INSERT INTO table_group_members (group_id, table_id) VALUES (?, ?)',
    ).bind(group.resolvedId, tableId)));
  }
  await c.env.DB.batch(statements);
  return c.json(await readLayout(c.env.DB, hallId));
});

async function parseLiveLayout(database: D1Database, hallId: number,
  body: Record<string, unknown>) {
  if (!Array.isArray(body.tables) || !Array.isArray(body.groups)) return undefined;
  const existing = await rows<{ id: number }>(database.prepare(
    'SELECT id FROM hall_tables WHERE hall_id = ? ORDER BY id',
  ).bind(hallId));
  const expectedIds = new Set(existing.map(({ id }) => id));
  const tables: Position[] = [];
  for (const raw of body.tables) {
    if (!isObject(raw)) return undefined;
    const id = Number(raw.id);
    const x = Number(raw.x);
    const y = Number(raw.y);
    if (!Number.isSafeInteger(id) || !expectedIds.has(id) ||
        !Number.isFinite(x) || !Number.isFinite(y) ||
        Math.abs(x) > 30_000 || Math.abs(y) > 30_000) return undefined;
    tables.push({ id, x, y });
  }
  if (tables.length !== expectedIds.size || new Set(tables.map(({ id }) => id)).size !== tables.length) {
    return undefined;
  }
  const groups: GroupInput[] = [];
  const members = new Set<number>();
  for (const raw of body.groups) {
    if (!isObject(raw) || !Array.isArray(raw.tableIds)) return undefined;
    const tableIds = raw.tableIds.map(Number);
    const id = Number(raw.id);
    if (!Number.isSafeInteger(id) || id === 0 || tableIds.length < 2 ||
        tableIds.some((tableId) => !Number.isSafeInteger(tableId) ||
          !expectedIds.has(tableId) || members.has(tableId))) return undefined;
    tableIds.forEach((tableId) => members.add(tableId));
    const fallback = tableIds.map((tableId) => String(tableId)).join(', ');
    const identifier = typeof raw.identifier === 'string' && raw.identifier.trim()
      ? raw.identifier.trim().slice(0, 80) : fallback;
    groups.push({ id, identifier, tableIds });
  }
  return { tables, groups };
}

async function currentGroups(database: D1Database, hallId: number) {
  const groups = await rows<{ id: number }>(database.prepare(
    'SELECT id FROM table_groups WHERE hall_id = ?',
  ).bind(hallId));
  const members = await rows<{ groupId: number; tableId: number }>(database.prepare(
    `SELECT m.group_id AS groupId, m.table_id AS tableId
     FROM table_group_members m JOIN table_groups g ON g.id = m.group_id
     WHERE g.hall_id = ?`,
  ).bind(hallId));
  return groups.map(({ id }) => ({
    id,
    tableIds: members.filter((member) => member.groupId === id)
      .map((member) => member.tableId).sort((a, b) => a - b),
  }));
}

function preservesActiveTargets(requested: GroupInput[], existing: Array<{
  id: number; tableIds: number[];
}>, active: Array<{ tableId: number | null; groupId: number | null }>) {
  for (const order of active) {
    if (order.groupId !== null) {
      const oldGroup = existing.find((group) => group.id === order.groupId);
      const newGroup = requested.find((group) => group.id === order.groupId);
      if (!oldGroup || !newGroup || oldGroup.tableIds.join(',') !==
          [...newGroup.tableIds].sort((a, b) => a - b).join(',')) return false;
    } else if (order.tableId !== null && requested.some(
      (group) => group.tableIds.includes(order.tableId!),
    )) return false;
  }
  return true;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default routes;
