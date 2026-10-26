import mutationApp from '../../mutationApp';
import { rows } from '../../shared/http';
import type { CloudBindings } from '../../types';
import { patchesForMutation, type RealtimePatch } from './events';

type SocketAttachment = {
  sessionId: number;
  userId: number;
  role: string;
  expiresAt: string;
  hallIds: number[];
  adminFeed: boolean;
};

export class RestaurantCoordinator implements DurableObject {
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly state: DurableObjectState,
    private readonly env: CloudBindings) {
    state.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }

  fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (request.headers.get('Upgrade')?.toLowerCase() === 'websocket') {
      return this.acceptSocket(request);
    }
    if (url.pathname.startsWith('/__session/') && request.method === 'DELETE') {
      this.closeSession(Number(url.pathname.split('/').at(-1)));
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    return this.enqueue(() => this.mutate(request));
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer) {
    const attachment = socket.deserializeAttachment() as SocketAttachment | null;
    if (!attachment) return socket.close(1011, 'Missing connection state');
    if (Date.parse(attachment.expiresAt) <= Date.now()) {
      return socket.close(1008, 'Session expired');
    }
    let data: Record<string, unknown>;
    try {
      const decoded = typeof message === 'string'
        ? message : new TextDecoder().decode(message);
      data = JSON.parse(decoded) as Record<string, unknown>;
    } catch {
      return this.send(socket, { type: 'error', error: 'INVALID_MESSAGE' });
    }
    if (data.type === 'subscribe-admin') {
      if (attachment.role !== 'admin') {
        return this.send(socket, { type: 'error', error: 'ADMIN_REQUIRED' });
      }
      attachment.adminFeed = true;
      socket.serializeAttachment(attachment);
      return this.send(socket, {
        type: 'subscribed-admin',
        versions: await this.versions([
          'catalog', 'dashboard', 'activity', 'halls', 'employees',
        ]),
      });
    }
    const hallId = Number(data.hallId ?? data.roomId);
    if (data.type !== 'subscribe' && data.type !== 'unsubscribe') {
      return this.send(socket, { type: 'error', error: 'UNKNOWN_MESSAGE' });
    }
    if (!Number.isSafeInteger(hallId) || hallId < 1) {
      return this.send(socket, { type: 'error', error: 'INVALID_HALL' });
    }
    if (data.type === 'unsubscribe') {
      attachment.hallIds = attachment.hallIds.filter((id) => id !== hallId);
      socket.serializeAttachment(attachment);
      return this.send(socket, { type: 'unsubscribed', hallId });
    }
    if (!await this.canAccessHall(attachment, hallId)) {
      return this.send(socket, { type: 'error', error: 'HALL_NOT_ASSIGNED', hallId });
    }
    if (!attachment.hallIds.includes(hallId)) attachment.hallIds.push(hallId);
    socket.serializeAttachment(attachment);
    return this.send(socket, {
      type: 'subscribed', hallId,
      versions: await this.versions([
        'catalog', `hall:${hallId}:layout`, `hall:${hallId}:orders`,
        `user:${attachment.userId}:assignments`,
      ]),
    });
  }

  webSocketClose() {}
  webSocketError() {}

  private async acceptSocket(request: Request) {
    const attachment = connectionAttachment(request.headers);
    if (!attachment) return new Response('Unauthorized', { status: 401 });
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.state.acceptWebSocket(server);
    server.serializeAttachment(attachment);
    this.send(server, {
      type: 'connected', protocolVersion: 1,
      serverTime: new Date().toISOString(),
    });
    return new Response(null, { status: 101, webSocket: client });
  }

  private async mutate(request: Request) {
    const actorId = Number(request.headers.get('x-realtime-user-id'));
    if (!Number.isSafeInteger(actorId) || actorId < 1) {
      return new Response(JSON.stringify({ error: 'INVALID_SESSION' }), {
        status: 401, headers: { 'content-type': 'application/json' },
      });
    }
    const idempotencyKey = request.headers.get('idempotency-key') ?? crypto.randomUUID();
    if (!/^[A-Za-z0-9_.:-]{8,128}$/.test(idempotencyKey)) {
      return new Response(JSON.stringify({ error: 'INVALID_IDEMPOTENCY_KEY' }), {
        status: 422, headers: { 'content-type': 'application/json' },
      });
    }
    const receipt = await this.env.DB.prepare(
      `SELECT status, response_body AS body FROM mutation_receipts
       WHERE actor_id = ? AND idempotency_key = ?`,
    ).bind(actorId, idempotencyKey).first<{ status: number; body: string }>();
    if (receipt) return jsonResponse(receipt.body, receipt.status, idempotencyKey, true);

    const startedAt = new Date().toISOString();
    const response = await mutationApp.fetch(request, this.env);
    const body = await response.clone().text();
    if (response.status >= 200 && response.status < 300) {
      await this.env.DB.prepare(
        `INSERT INTO mutation_receipts
         (actor_id, idempotency_key, operation, status, response_body, created_at)
         VALUES (?, ?, ?, ?, ?, ?)`,
      ).bind(actorId, idempotencyKey,
        `${request.method} ${new URL(request.url).pathname}`,
        response.status, body, startedAt).run();
      const parsed = body ? safeJson(body) : {};
      const patches = await patchesForMutation(
        this.env.DB, request, parsed, idempotencyKey, startedAt,
      );
      for (const patch of patches) this.broadcast(patch);
    }
    const headers = new Headers(response.headers);
    headers.set('Idempotency-Key', idempotencyKey);
    return new Response(body || null, { status: response.status, headers });
  }

  private broadcast(patch: RealtimePatch) {
    for (const socket of this.state.getWebSockets()) {
      const attachment = socket.deserializeAttachment() as SocketAttachment | null;
      if (!attachment) continue;
      if (Date.parse(attachment.expiresAt) <= Date.now()) {
        socket.close(1008, 'Session expired');
        continue;
      }
      const hallMatch = patch.hallId !== undefined &&
        attachment.hallIds.includes(patch.hallId);
      const catalogMatch = patch.audienceHallIds?.some(
        (hallId) => attachment.hallIds.includes(hallId),
      ) === true;
      const userMatch = patch.audienceUserIds?.includes(attachment.userId) === true;
      if ((patch.adminOnly && !attachment.adminFeed) ||
          (!patch.adminOnly && !attachment.adminFeed && !hallMatch &&
            !catalogMatch && !userMatch)) continue;
      this.send(socket, patch);
    }
  }

  private closeSession(sessionId: number) {
    for (const socket of this.state.getWebSockets()) {
      const attachment = socket.deserializeAttachment() as SocketAttachment | null;
      if (attachment?.sessionId === sessionId) socket.close(1008, 'Session revoked');
    }
  }

  private async canAccessHall(attachment: SocketAttachment, hallId: number) {
    if (attachment.role === 'admin') {
      return await this.env.DB.prepare('SELECT 1 FROM hall WHERE id = ?')
        .bind(hallId).first() !== null;
    }
    return await this.env.DB.prepare(
      'SELECT 1 FROM employee_halls WHERE user_id = ? AND hall_id = ?',
    ).bind(attachment.userId, hallId).first() !== null;
  }

  private async versions(resources: string[]) {
    if (resources.length === 0) return {};
    const values = await rows<{ resource: string; version: number }>(this.env.DB.prepare(
      `SELECT resource, version FROM resource_versions
       WHERE resource IN (${resources.map(() => '?').join(',')})`,
    ).bind(...resources));
    return Object.fromEntries(resources.map((resource) => [
      resource, values.find((row) => row.resource === resource)?.version ?? 0,
    ]));
  }

  private send(socket: WebSocket, value: unknown) {
    try { socket.send(JSON.stringify(value)); } catch { /* disconnected */ }
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.tail.then(operation, operation);
    this.tail = result.then(() => undefined, () => undefined);
    return result;
  }
}

function connectionAttachment(headers: Headers): SocketAttachment | undefined {
  const sessionId = Number(headers.get('x-realtime-session-id'));
  const userId = Number(headers.get('x-realtime-user-id'));
  const role = headers.get('x-realtime-role');
  const expiresAt = headers.get('x-realtime-expires-at');
  if (!Number.isSafeInteger(sessionId) || !Number.isSafeInteger(userId) ||
      !role || !expiresAt || Number.isNaN(Date.parse(expiresAt))) return undefined;
  return { sessionId, userId, role, expiresAt, hallIds: [], adminFeed: false };
}

function safeJson(body: string): unknown {
  try { return JSON.parse(body); } catch { return {}; }
}

function jsonResponse(body: string, status: number, key: string, replayed: boolean) {
  return new Response(body || null, { status, headers: {
    'content-type': 'application/json', 'Idempotency-Key': key,
    'Idempotency-Replayed': String(replayed),
  } });
}
