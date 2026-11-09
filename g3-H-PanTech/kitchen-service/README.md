# Kitchen Service

Cola priorizada de cocina (Go, puerto interno `8001`, no se publica al host). Se llama a través del API Gateway, salvo `POST /queue/orders`, que solo llama el Worker directo con `X-Service-Token`. Contratos: [`docs/equipo/contratos.md`](../docs/equipo/contratos.md).

## Endpoints

| Método | Ruta | Autorización | Notas |
|---|---|---|---|
| POST | `/queue/orders` | `X-Service-Token` = `SERVICE_TOKEN` (si no, `401`) | Idempotente por `order_id`: `201` al crear, `200` con el existente |
| GET | `/queue` | `kitchen:read` o `kitchen:deliver` | Filtro opcional `?status=`; con solo `kitchen:deliver` devuelve solo pedidos `listo` |
| GET | `/queue/orders/{id}` | `kitchen:read` o `kitchen:deliver` | Detalle y ETA; con solo `kitchen:deliver` los que no están `listo` responden `404` |
| PATCH | `/queue/orders/{id}/status` | `kitchen:update` | Cuerpo `{"status":"en_preparacion"\|"listo"}` |
| POST | `/queue/orders/{id}/deliver` | `kitchen:deliver` | Exige estado `listo` |
| GET | `/health` | Ninguna | `200 {"status":"ok"}` |
| GET | `/ready` | Ninguna | `200 {"status":"ready"}` o `503 {"status":"not_ready","dependencies":{...}}`; verifica PostgreSQL, Redis y RabbitMQ |

Sin `X-User-Id` → `401 UNAUTHENTICATED`; permiso ausente → `403 FORBIDDEN`. Cada servicio valida los permisos aunque el Gateway ya lo haya hecho. Errores con `{"code","message","request_id","details":{}}`:

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo o parámetros inválidos |
| 404 | `NOT_FOUND` | La comanda no existe (o ya expiró) |
| 409 | `INVALID_STATE` | Transición no permitida; `details` trae `current_status` y `requested_status` |
| 409 | `KITCHEN_AT_CAPACITY` | La cocina está llena; el pedido sigue `pendiente`; `details` trae `max` e `in_preparation` |

### `POST /queue/orders`

```json
{
  "order_id": "UUID",
  "customer_id": "UUID",
  "order_type": "para_llevar | en_mesa",
  "paid_at": "2026-11-09T14:50:00Z",
  "items": [
    {"product_id": "UUID", "name": "Croissant", "quantity": 2, "prep_minutes": 5}
  ]
}
```

- `items` es opcional. Sin `items` el pedido usa `DEFAULT_PREP_MINUTES` y no se le aplica SJF por producto.
- `prep_minutes` del pedido = **máximo** de `prep_minutes` de sus ítems (se cocinan en paralelo). Un ítem sin `prep_minutes` toma el valor de `kitchen.prep_times` y, si no existe, `DEFAULT_PREP_MINUTES`.
- Validación fallida → `400 VALIDATION_ERROR` con el detalle por campo.

### `GET /queue`

```json
{
  "items": [{
    "order_id": "...", "customer_id": "...", "order_type": "para_llevar",
    "status": "pendiente", "prep_minutes": 12, "priority_score": 11.99,
    "wait_minutes": 0.02, "position": 1, "eta_minutes": 12,
    "enqueued_at": "...", "started_at": null, "ready_at": null, "items": []
  }],
  "capacity": {"max": 4, "in_preparation": 0},
  "generated_at": "..."
}
```

Por defecto devuelve pedidos activos (`pendiente`, `en_preparacion`, `listo`), nunca `entregado`. `position` es la posición (desde 1) en la cola activa completa.

## Estados

```text
pendiente -> en_preparacion -> listo -> entregado
```

Cualquier otra transición responde `409 INVALID_STATE`. Pasar a `en_preparacion` con la cocina llena responde `409 KITCHEN_AT_CAPACITY`. Cada transición valida el estado y la capacidad en un solo paso atómico (script Lua en Redis), así que dos peticiones simultáneas nunca superan `KITCHEN_CAPACITY`.

## Algoritmo

```text
priority_score = prep_minutes - AGING_FACTOR * wait_minutes     (menor = primero)
```

Orden de la respuesta:

1. `en_preparacion` (no compiten por prioridad: ocupan capacidad), el más antiguo primero.
2. `pendiente` por `priority_score` ascendente; empate → FIFO por `enqueued_at`; último desempate, `order_id`.
3. `listo`, por orden de llegada.

- **SJF:** a igual espera, sale primero el pedido más corto.
- **Aging:** cada minuto en espera baja el score, así un pedido viejo y largo termina pasando a uno nuevo y corto. Ejemplo con `AGING_FACTOR=0.5`: un pedido de 30 min con 70 min de espera vale `30 - 35 = -5` y supera a uno nuevo de 3 min.
- El score se recalcula al encolar y en cada `GET /queue` (y se reescribe en el `ZSET`).
- **ETA** (`eta_minutes`): simulación por cupos. La cocina tiene la capacidad vigente en cupos; cada pedido en preparación ocupa uno hasta cumplir su tiempo restante; los cupos libres están disponibles ya. Los pendientes, en orden de prioridad, toman el cupo que se libera primero y terminan `prep_minutes` después. Los `listo` valen `0`. Se redondea hacia arriba.

## Capacidad

`KITCHEN_CAPACITY` solo siembra la fila de `kitchen.kitchen_capacity` la primera vez. Después manda la tabla (se puede cambiar con SQL sin reiniciar); si no responde se usa el valor de la variable.

## Evento `order.ready`

Al pasar a `listo` se publica en el exchange `panaderia.events` (topic, durable) con routing key `order.ready` y el sobre de `contratos.md` §4:

```json
{
  "event_id": "UUID", "event_type": "order.ready", "version": 1,
  "occurred_at": "2026-11-09T14:45:00Z", "producer": "kitchen-service",
  "payload": {"order_id": "UUID", "customer_id": "UUID", "ready_at": "2026-11-09T14:45:00Z", "prep_minutes": 12}
}
```

**Si RabbitMQ falla no se revierte el estado.** El evento se guarda primero en un outbox en Redis (`kitchen:outbox`) y un worker lo publica con confirmación del broker; ante un fallo registra el error y reintenta con backoff exponencial (1 s a 30 s). El `event_id` no cambia entre reintentos, así que el consumidor puede deduplicar. Mensajes persistentes; el exchange se declara con los mismos argumentos que `definitions.json`.

## PostgreSQL (esquema `kitchen`)

Las migraciones de `migrations/` se aplican solas al arrancar, una vez cada una (`kitchen.schema_migrations`) y con un candado para varias réplicas. No se toca `init.sql`.

| Tabla | Contenido |
|---|---|
| `prep_times` | Tiempo base por `product_id` |
| `order_status_events` | Una fila por transición: `status`, `changed_at`, `changed_by` (`X-User-Id`; nulo cuando encola el Worker) |
| `kitchen_capacity` | Fila única con `max_orders` |

Si el historial no se puede escribir, se registra el error y el cambio de estado se mantiene.

## Redis

Base de `REDIS_QUEUE_URL` (AOF activo en `infrastructure/redis/redis.conf`):

| Llave | Tipo | Contenido |
|---|---|---|
| `kitchen:queue` | ZSET | miembro = `order_id`, score = `priority_score` |
| `kitchen:order:<order_id>` | HASH | detalle de la comanda (estado, tiempos, ítems en JSON) |
| `kitchen:in_preparation` | SET | pedidos en preparación (para la capacidad) |
| `kitchen:outbox` | HASH | eventos pendientes de publicar |

Los entregados salen del `ZSET` y el hash se conserva 24 h.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `PORT` | No (`8001`) | Puerto de escucha |
| `DATABASE_URL` | Sí | PostgreSQL; si está vacía se usa `DATABASE_URL_GO` |
| `REDIS_QUEUE_URL` | Sí | Redis de la cola |
| `RABBITMQ_URL` | Sí | Broker |
| `RABBITMQ_EXCHANGE` | No (`panaderia.events`) | Exchange de eventos |
| `SERVICE_TOKEN` | Sí | Secreto para `POST /queue/orders` |
| `AGING_FACTOR` | No (`0.5`) | Descuento de score por minuto de espera |
| `KITCHEN_CAPACITY` | No (`4`) | Capacidad inicial (siembra la tabla) |
| `DEFAULT_PREP_MINUTES` | No (`5`) | Minutos si no hay tiempo base |

Ver [`.env.example`](.env.example).

## Correr y probar

```powershell
cd kitchen-service
gofmt -l .
go vet ./...
go test ./...
```

Las pruebas de integración levantan PostgreSQL y Redis con Docker y **se saltan solas** si Docker no está disponible (o con `go test -short ./...`). Las demás usan miniredis y no necesitan Docker.

Con Docker Compose:

```powershell
docker compose -f docker-compose.p2.yml --env-file .env.p2.example up -d --build postgres redis rabbitmq rabbitmq-init kitchen-service
```

## Estructura

```text
cmd/kitchen/main.go           arranque
internal/config               variables de entorno
internal/http                 rutas, handlers, autorización y errores
internal/queue                prioridad, capacidad, ETA, transiciones (Redis) y servicio
internal/repository           PostgreSQL: migraciones, tiempos, historial y capacidad
internal/events               publicador RabbitMQ y outbox
migrations/                   SQL del esquema kitchen
tests/                        prioridad, ETA, handlers, transiciones, eventos e integración
```
