# Infraestructura de métricas (acceso a MongoDB)

Adaptador del lado del Worker para las **métricas de ventas acumuladas**, cuyo
dueño es MongoDB (no D1). Como los Cloudflare Workers no abren TCP crudo, el
Worker no habla con Mongo directamente: usa `MetricsClient` (en `client.ts`),
que se comunica por HTTP con el `metrics-gateway` (ver
`services/metrics_gateway/`).

## Entradas

- `MetricsClient` / `metricsClient(env)` — cliente HTTP hacia el gateway.
  - `apply(mutationId, operations)` — aplica operaciones idempotentes de upsert
    con `$inc`/`$set` (el `mutationId` evita doble conteo en reintentos).
  - `rebuild(mutationId, snapshot)` — reemplaza todas las colecciones con un
    snapshot recomputado por el Worker desde D1 (resincronización).
  - `overview`, `hallStatusCounts`, `hallTotals` — lecturas para dashboards.
- Tipos `MetricOperation`, `MetricSnapshot`, `OverviewResult`, etc.

## Contrato de negocio

La lógica (qué contar, qué deltas, qué claves) vive en
`features/admin/sales_metrics.ts`, que **produce** las `MetricOperation` y el
`MetricSnapshot`. Este adaptador solo los transporta. El gateway solo los
persiste. La división es deliberada: el negocio en el Worker, el acceso a datos
aquí, el almacenamiento en Mongo.

## Degradación sin gateway

Si `METRICS_GATEWAY_URL` no está configurada (tests, o un stack sin Mongo),
`enabled` es `false` y todas las llamadas se vuelven no-ops / resultados vacíos.
Así una mutación de orden nunca falla por ausencia de métricas.
