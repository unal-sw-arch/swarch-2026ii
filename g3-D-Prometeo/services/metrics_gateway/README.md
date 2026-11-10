# Metrics gateway (gateway de datos, no un backend)

Este servicio existe por **una sola razón técnica**: los Cloudflare Workers
(incluido `wrangler dev`) no pueden abrir conexiones TCP crudas, y el driver
oficial de MongoDB las necesita. El Worker (`apps/cloud_api`) por tanto no puede
hablar con Mongo directamente, así que le habla por HTTP a este proceso, que sí
usa el driver `mongodb` y traduce HTTP ↔ Mongo.

## Qué es y qué NO es

- **Es** un *gateway de datos* puro: traduce peticiones HTTP a operaciones Mongo
  (upsert con `$inc`, lecturas, rebuild). No contiene reglas de negocio.
- **No es** un backend paralelo. El único backend de la aplicación sigue siendo
  `apps/cloud_api`. Toda la lógica de negocio (qué se cuenta, cuándo, cómo se
  calculan los deltas) vive en el Worker; aquí solo se persiste y se lee.

Esto es una excepción acotada y documentada a la regla "`apps/cloud_api` es el
único backend": el gateway no decide nada, solo da acceso a Mongo desde un
runtime que no puede conectarse por TCP.

## Alcance de datos

Dueño de las métricas de ventas acumuladas (antes tablas en D1):

| Colección Mongo            | Clave (`_id` compuesto)      | Reemplaza a la tabla D1      |
| -------------------------- | ---------------------------- | ---------------------------- |
| `hall_sales_totals`        | `hallId`                     | `hall_sales_totals`          |
| `hall_order_status_totals` | `hallId:status`              | `hall_order_status_totals`   |
| `sales_metric_buckets`     | `period:bucketKey`           | `sales_metric_buckets`       |
| `daily_product_sales`      | `dayKey:productId`           | `daily_product_sales`        |
| `daily_category_sales`     | `dayKey:categoryId`          | `daily_category_sales`       |

Las órdenes, salones, usuarios y el tiempo real **siguen en D1**. Este servicio
no los toca.

## Solo desarrollo local

Se levanta vía `docker-compose`. En producción el Worker corre en la edge de
Cloudflare y este gateway no aplica tal cual (allí se usaría un acceso a Mongo
adecuado al entorno). No guardar secretos reales aquí.

## Endpoints

- `GET  /health` — comprobación de vida.
- `POST /metrics/apply` — aplica un lote de operaciones idempotentes de métrica
  (upserts con `$inc`). Cuerpo: `{ mutationId, operations: [...] }`.
- `GET  /metrics/overview?period=&keys=&todayKey=` — datos del dashboard.
- `GET  /metrics/hall-status/:hallId` — conteo por estado de un salón.
- `GET  /metrics/hall-totals` — totales por salón (para unir con `hall` en el Worker).
- `POST /metrics/rebuild` — reemplaza colecciones completas con un snapshot
  recomputado por el Worker desde D1 (red de seguridad de resincronización).

El contrato exacto de cada operación lo define el Worker en
`apps/cloud_api/infrastructure/metrics/`.
