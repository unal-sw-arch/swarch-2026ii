# Administración

Actividad, historial, métricas acumuladas, empleados y edición administrativa de salones. Los cálculos reutilizables viven en `helpers.ts` y `sales_metrics.ts`.

## Métricas de ventas (dueño: MongoDB)

Las métricas acumuladas ya no viven en D1: su dueño es MongoDB, al que se
accede a través del `metrics-gateway` (ver `services/metrics_gateway/` y
`infrastructure/metrics/`).

- `sales_metrics.ts` ya no genera SQL para D1. Produce operaciones y snapshots
  de métrica (`MetricOperation[]` / `MetricSnapshot`): `statusMetricChange`,
  `statusMetricTransition`, `billedOrderMetricOperations` (facturar/revertir), y
  `rebuildMetricSnapshot` (recomputa todo desde las órdenes cerradas de D1).
- Las escrituras ocurren **después** del batch de D1 de la orden, no dentro de
  él: D1 sigue siendo la fuente de verdad transaccional de la orden; la métrica
  es un paso aparte e idempotente por `idempotency-key`. El borrado masivo
  recomputa el snapshot completo tras confirmar el borrado.
- Las lecturas (`/overview`, `/halls/:id/order-history-summary`) consultan el
  gateway, no D1. El shape JSON de respuesta no cambió.
- El tiempo real no cambió: el bump de `resource_versions('dashboard')` sigue en
  D1 y los clientes re-consultan por HTTP.
