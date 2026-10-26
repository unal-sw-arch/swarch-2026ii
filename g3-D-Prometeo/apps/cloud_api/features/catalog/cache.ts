import { readCatalog } from '../read_model/repository';

export type CatalogSnapshot = Awaited<ReturnType<typeof readCatalog>>;
export type CatalogCacheStatus = 'HIT' | 'MISS';

const cachePath = '/__internal/catalog-cache';

/**
 * Keeps the expensive assembled catalog in the Workers Cache API. The D1
 * resource version is part of the key, so catalog writes immediately move all
 * readers to a fresh key without requiring a cache-wide purge.
 */
export async function readCachedCatalog(database: D1Database, requestUrl: string):
Promise<{ catalog: CatalogSnapshot; status: CatalogCacheStatus; version: number }> {
  const versionRow = await database.prepare(
    "SELECT version FROM resource_versions WHERE resource = 'catalog'",
  ).first<{ version: number }>();
  const version = Number(versionRow?.version ?? 0);
  const origin = new URL(requestUrl).origin;
  const key = new Request(`${origin}${cachePath}/v${version}`, { method: 'GET' });
  const cache = await caches.open('restauranteapp-catalog');
  const cached = await cache.match(key);
  if (cached) {
    return {
      catalog: await cached.json<CatalogSnapshot>(),
      status: 'HIT',
      version,
    };
  }

  const catalog = await readCatalog(database);
  await cache.put(key, new Response(JSON.stringify(catalog), {
    headers: {
      'content-type': 'application/json; charset=UTF-8',
      // Old versioned entries are harmless and age out automatically.
      'cache-control': 'public, max-age=3600',
    },
  }));
  return { catalog, status: 'MISS', version };
}
