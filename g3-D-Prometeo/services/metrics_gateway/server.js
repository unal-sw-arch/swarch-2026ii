// Metrics gateway: translates HTTP calls from the Cloudflare Worker into
// MongoDB operations. Pure data access — no business logic lives here. See
// README.md for why this process exists (Workers cannot open raw TCP sockets).

import { createServer } from 'node:http';
import { MongoClient } from 'mongodb';

const PORT = Number(process.env.PORT ?? 8788);
const MONGO_URL = process.env.MONGO_URL ?? 'mongodb://mongo:27017';
const DB_NAME = process.env.MONGO_DB ?? 'restauranteapp_metrics';

// Collection names mirror the retired D1 tables one-to-one.
const COLLECTIONS = {
  hallSalesTotals: 'hall_sales_totals',
  hallOrderStatusTotals: 'hall_order_status_totals',
  salesMetricBuckets: 'sales_metric_buckets',
  dailyProductSales: 'daily_product_sales',
  dailyCategorySales: 'daily_category_sales',
};

// A processed-mutations collection makes /metrics/apply idempotent: the same
// mutationId applied twice is a no-op, so coordinator retries never double-count.
const PROCESSED = 'processed_mutations';

const client = new MongoClient(MONGO_URL);
let db;

async function connect() {
  await client.connect();
  db = client.db(DB_NAME);
  await db.collection(PROCESSED).createIndex({ mutationId: 1 }, { unique: true });
  console.log(`metrics-gateway connected to ${MONGO_URL}/${DB_NAME}`);
}

// --- Operation handlers -----------------------------------------------------

// Each operation is a small, declarative instruction produced by the Worker.
// `inc` increments numeric fields (clamped to >= 0 to match the old
// MAX(0, col + delta) UPSERTs); `set` overwrites fields (names, timestamps).
async function applyOperation(operation) {
  const collection = COLLECTIONS[operation.collection];
  if (!collection) throw new Error(`Unknown collection: ${operation.collection}`);
  const target = db.collection(collection);
  const filter = { _id: operation.id };
  const update = {};
  if (operation.inc && Object.keys(operation.inc).length > 0) update.$inc = operation.inc;
  if (operation.set && Object.keys(operation.set).length > 0) update.$set = operation.set;
  if (operation.setOnInsert) update.$setOnInsert = operation.setOnInsert;
  if (Object.keys(update).length === 0) return;
  await target.updateOne(filter, update, { upsert: true });
  // Clamp any field that may have gone negative back to zero, preserving the
  // old SQL semantics without needing a second round-trip per field.
  if (operation.clamp && operation.clamp.length > 0) {
    const doc = await target.findOne(filter);
    const fixes = {};
    for (const field of operation.clamp) {
      if (doc && typeof doc[field] === 'number' && doc[field] < 0) fixes[field] = 0;
    }
    if (Object.keys(fixes).length > 0) {
      await target.updateOne(filter, { $set: fixes });
    }
  }
}

async function applyBatch(body) {
  const { mutationId, operations } = body;
  if (!mutationId || !Array.isArray(operations)) {
    throw new HttpError(422, 'mutationId and operations[] are required');
  }
  // No multi-document transaction: a standalone Mongo (single container) does
  // not support them. Idempotency comes from the unique index on mutationId —
  // if the insert hits a duplicate key, this batch already ran and we skip it.
  // The metric upserts are individually idempotent, so a retry is safe.
  try {
    await db.collection(PROCESSED).insertOne(
      { mutationId, appliedAt: new Date().toISOString() },
    );
  } catch (error) {
    if (error?.code === 11000) return { applied: false }; // already processed
    throw error;
  }
  for (const operation of operations) await applyOperation(operation);
  return { applied: true };
}

// Replaces whole collections with a freshly recomputed snapshot from the Worker
// (which recomputes from D1 orders). Used by bulk delete and manual resync.
async function rebuild(body) {
  const { snapshot } = body;
  if (!snapshot || typeof snapshot !== 'object') {
    throw new HttpError(422, 'snapshot is required');
  }
  for (const [key, collection] of Object.entries(COLLECTIONS)) {
    if (!Array.isArray(snapshot[key])) continue;
    const target = db.collection(collection);
    await target.deleteMany({});
    if (snapshot[key].length > 0) await target.insertMany(snapshot[key]);
  }
  return { rebuilt: true };
}

async function overview(query) {
  const period = query.get('period') ?? 'day';
  const todayKey = query.get('todayKey') ?? '';
  const keys = (query.get('keys') ?? '').split(',').filter(Boolean);
  const buckets = db.collection(COLLECTIONS.salesMetricBuckets);
  const [summary, points, topProduct, categories] = await Promise.all([
    buckets.findOne({ _id: `day:${todayKey}` }),
    keys.length === 0 ? [] : buckets.find({
      _id: { $in: keys.map((key) => `${period}:${key}`) },
    }).toArray(),
    db.collection(COLLECTIONS.dailyProductSales).find({ dayKey: todayKey })
      .sort({ quantity: -1, productName: 1, productId: 1 }).limit(1).toArray(),
    db.collection(COLLECTIONS.dailyCategorySales).find({ dayKey: todayKey })
      .sort({ totalSales: -1, categoryName: 1, categoryId: 1 }).limit(4).toArray(),
  ]);
  return {
    summary: summary ? { sales: summary.totalSales, orders: summary.orderCount } : null,
    points: points.map((point) => ({ key: point.bucketKey, value: point.totalSales })),
    topProduct: topProduct[0]
      ? { name: topProduct[0].productName, value: topProduct[0].unitValue,
        quantity: topProduct[0].quantity }
      : null,
    categories: categories.map((category) => ({
      name: category.categoryName, value: category.totalSales,
    })),
  };
}

async function hallStatus(hallId) {
  const docs = await db.collection(COLLECTIONS.hallOrderStatusTotals)
    .find({ hallId: Number(hallId) }).toArray();
  return { counts: docs.map((doc) => ({ status: doc.status, count: doc.orderCount })) };
}

async function hallTotals() {
  const docs = await db.collection(COLLECTIONS.hallSalesTotals).find({}).toArray();
  return {
    totals: docs.map((doc) => ({
      hallId: doc.hallId, orderCount: doc.orderCount, totalSales: doc.totalSales,
    })),
  };
}

// --- HTTP plumbing ----------------------------------------------------------

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(422, 'Invalid JSON body');
  }
}

function sendJson(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  response.end(body);
}

async function route(request, url) {
  const { pathname } = url;
  if (request.method === 'GET' && pathname === '/health') return { ok: true };
  if (request.method === 'POST' && pathname === '/metrics/apply') {
    return applyBatch(await readJson(request));
  }
  if (request.method === 'POST' && pathname === '/metrics/rebuild') {
    return rebuild(await readJson(request));
  }
  if (request.method === 'GET' && pathname === '/metrics/overview') {
    return overview(url.searchParams);
  }
  if (request.method === 'GET' && pathname === '/metrics/hall-totals') {
    return hallTotals();
  }
  const hallStatusMatch = pathname.match(/^\/metrics\/hall-status\/(\d+)$/);
  if (request.method === 'GET' && hallStatusMatch) {
    return hallStatus(hallStatusMatch[1]);
  }
  throw new HttpError(404, 'Not found');
}

const server = createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`);
  route(request, url)
    .then((result) => sendJson(response, 200, result))
    .catch((error) => {
      const status = error instanceof HttpError ? error.status : 500;
      if (status >= 500) console.error('metrics-gateway error:', error);
      sendJson(response, status, { error: error.message ?? 'Internal error' });
    });
});

connect()
  .then(() => server.listen(PORT, '0.0.0.0',
    () => console.log(`metrics-gateway listening on :${PORT}`)))
  .catch((error) => {
    console.error('metrics-gateway failed to start:', error);
    process.exit(1);
  });
