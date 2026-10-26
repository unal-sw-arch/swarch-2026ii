import { rows } from '../../shared/http';
import { bogotaMetricKeys } from './sales_metrics';

const allowedRoles = new Set(['waiter', 'kitchen', 'cashier', 'manager']);

export function hallSummary(id: number, name: string) {
  return { id, name, tableCount: 0, orderCount: 0, totalSales: 0, averageSale: 0 };
}

export async function employeeWithHalls(database: D1Database,
  employee: { id: number; fullName: string; username: string; role: string }) {
  const assigned = await rows<{ hallId: number }>(database.prepare(
    'SELECT hall_id AS hallId FROM employee_halls WHERE user_id = ? ORDER BY hall_id',
  ).bind(employee.id));
  return { ...employee, roomIds: assigned.map(({ hallId }) => hallId) };
}

export function employeeInput(body: Record<string, unknown>, passwordRequired: boolean) {
  const fullName = typeof body.fullName === 'string' ? body.fullName.trim() : '';
  const username = typeof body.username === 'string' ? body.username.trim().toLowerCase() : '';
  const password = typeof body.password === 'string' ? body.password : undefined;
  const role = typeof body.role === 'string' ? body.role : '';
  const roomIds = idArray(body.roomIds);
  if (fullName.length < 2 || fullName.length > 100 ||
      !/^[a-z0-9](?:[a-z0-9._-]{1,30}[a-z0-9])?$/.test(username) ||
      !allowedRoles.has(role) || roomIds === null ||
      (passwordRequired && (password === undefined || password.length < 12)) ||
      (password?.length ?? 0) > 128) return undefined;
  return { fullName, username, password, role, roomIds };
}

export function idArray(value: unknown): number[] | null {
  if (!Array.isArray(value)) return null;
  const ids = value.map(Number);
  return ids.every((id) => Number.isSafeInteger(id) && id > 0) &&
    new Set(ids).size === ids.length ? ids : null;
}

export async function hallsExist(database: D1Database, ids: number[]) {
  if (ids.length === 0) return true;
  const result = await database.prepare(
    `SELECT COUNT(*) AS count FROM hall WHERE id IN (${ids.map(() => '?').join(',')})`,
  ).bind(...ids).first<{ count: number }>();
  return Number(result?.count ?? 0) === ids.length;
}

type Geometry = { x: number; y: number; width: number; height: number; rotation: number };
type EditorTable = Geometry & { id: number; identifier: string };
type EditorGroup = { id: number; identifier: string; tableIds: number[] };

export function parseEditorLayout(body: Record<string, unknown>) {
  if (!Array.isArray(body.tables) || !Array.isArray(body.walls) ||
      !Array.isArray(body.groups)) return undefined;
  const tables: EditorTable[] = [];
  const identifiers = new Set<string>();
  const ids = new Set<number>();
  for (const raw of body.tables) {
    if (!object(raw)) return undefined;
    const id = Number(raw.id);
    const identifier = typeof raw.identifier === 'string' ? raw.identifier.trim() : '';
    const geometry = geometryOf(raw, 32);
    if (!Number.isSafeInteger(id) || id === 0 || ids.has(id) || !identifier ||
        identifiers.has(identifier) || !geometry) return undefined;
    ids.add(id); identifiers.add(identifier);
    tables.push({ id, identifier, ...geometry });
  }
  const walls: Geometry[] = [];
  for (const raw of body.walls) {
    if (!object(raw)) return undefined;
    const geometry = geometryOf(raw, 2);
    if (!geometry) return undefined;
    walls.push(geometry);
  }
  const members = new Set<number>();
  const groups: EditorGroup[] = [];
  for (const raw of body.groups) {
    if (!object(raw) || !Array.isArray(raw.tableIds)) return undefined;
    const id = Number(raw.id);
    const tableIds = raw.tableIds.map(Number);
    if (!Number.isSafeInteger(id) || id === 0 || tableIds.length < 2 ||
        tableIds.some((tableId) => !ids.has(tableId) || members.has(tableId))) return undefined;
    tableIds.forEach((id) => members.add(id));
    const identifier = typeof raw.identifier === 'string' && raw.identifier.trim()
      ? raw.identifier.trim().slice(0, 80) : tableIds.join(', ');
    groups.push({ id, identifier, tableIds });
  }
  return { tables, walls, groups };
}

export function geometryOf(value: Record<string, unknown>, minimum: number): Geometry | undefined {
  const x = Number(value.x); const y = Number(value.y);
  const width = Number(value.width); const height = Number(value.height);
  const rotation = Number(value.rotation ?? 0);
  return [x, y, width, height, rotation].every(Number.isFinite) &&
    Math.abs(x) <= 30_000 && Math.abs(y) <= 30_000 &&
    width >= minimum && height >= minimum && width <= 5_000 && height <= 5_000
    ? { x, y, width, height, rotation } : undefined;
}

export function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function bogotaParts(value: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(value);
  const get = (type: string) => Number(parts.find((part) => part.type === type)?.value);
  return { year: get('year'), month: get('month'), day: get('day') };
}

export function localIso(year: number, month: number, day: number, hour = 0) {
  return new Date(`${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00-05:00`).toISOString();
}


export function revenueBuckets(period: 'minute' | 'hour' | 'day' | 'month' | 'year',
  range: number, todayStart: string, now: Date) {
  const start = new Date(todayStart);
  const result: Array<{ key: string; label: string }> = [];
  if (period === 'minute') {
    const end = new Date(Math.floor(now.getTime() / 60_000) * 60_000);
    for (let offset = range - 1; offset >= 0; offset--) {
      const from = new Date(end.getTime() - offset * 60_000);
      const key = bogotaMetricKeys(from).minute;
      result.push({ key, label: key.slice(-5) });
    }
    return result;
  }
  if (period === 'hour') {
    for (let hour = 0; hour < 24; hour++) {
      const from = new Date(start.getTime() + hour * 3_600_000);
      result.push({ key: bogotaMetricKeys(from).hour,
        label: `${hour.toString().padStart(2, '0')}:00` });
    }
    return result;
  }
  if (period === 'day') {
    for (let offset = range - 1; offset >= 0; offset--) {
      const from = new Date(start.getTime() - offset * 86_400_000);
      result.push({ key: bogotaMetricKeys(from).day,
        label: `${from.getUTCDate()}/${from.getUTCMonth() + 1}` });
    }
    return result;
  }
  const date = bogotaParts(start);
  for (let offset = range - 1; offset >= 0; offset--) {
    const year = period === 'year' ? date.year - offset : date.year;
    const month = period === 'month' ? date.month - offset : 1;
    const from = new Date(Date.UTC(year, month - 1, 1, 5));
    result.push({ key: bogotaMetricKeys(from)[period],
      label: period === 'year' ? String(year) : `${from.getUTCMonth() + 1}/${from.getUTCFullYear()}` });
  }
  return result;
}
