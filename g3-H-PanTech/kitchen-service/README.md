# Kitchen Service

Cola priorizada de cocina (Go, puerto interno `8001`, no se publica al host). Se llama a través del API Gateway, salvo `POST /queue/orders`, que solo llama el Worker directo con `X-Service-Token`. Contratos: [`docs/equipo/contratos.md`](../docs/equipo/contratos.md).

> **Estado:** etapa 1 (encolar, consultar la cola, salud). Cambio de estado, entrega, capacidad en `409`, PostgreSQL y `order.ready` llegan en la etapa 3.

## Endpoints

| Método | Ruta | Autorización | Notas |
|---|---|---|---|
| POST | `/queue/orders` | `X-Service-Token` = `SERVICE_TOKEN` (si no, `401`) | Idempotente por `order_id`: `201` al crear, `200` con el existente |
| GET | `/queue` | `kitchen:read` o `kitchen:deliver` | Filtro opcional `?status=`; con solo `kitchen:deliver` devuelve solo pedidos `listo` |
| GET | `/health` | Ninguna | `200 {"status":"ok"}` |
| GET | `/ready` | Ninguna | `200 {"status":"ready"}` o `503 {"status":"not_ready","dependencies":{...}}` |

Sin `X-User-Id` → `401 UNAUTHENTICATED`; permiso ausente → `403 FORBIDDEN`. Errores con `{"code","message","request_id","details":{}}`.

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
- `prep_minutes` del pedido = **máximo** de `prep_minutes` de sus ítems (se cocinan en paralelo). Un ítem sin `prep_minutes` toma el valor de la tabla `prep_times` (etapa 3) y, si no existe, `DEFAULT_PREP_MINUTES`.
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
- **ETA** (`eta_minutes`): simulación por cupos. La cocina tiene `KITCHEN_CAPACITY` cupos; cada pedido en preparación ocupa uno hasta cumplir su tiempo restante; los cupos libres están disponibles ya. Los pendientes, en orden de prioridad, toman el cupo que se libera primero y terminan `prep_minutes` después. Los `listo` valen `0`. Se redondea hacia arriba.

## Redis

Base de `REDIS_QUEUE_URL` (AOF activo en `infrastructure/redis/redis.conf`):

| Llave | Tipo | Contenido |
|---|---|---|
| `kitchen:queue` | ZSET | miembro = `order_id`, score = `priority_score` |
| `kitchen:order:<order_id>` | HASH | detalle de la comanda (estado, tiempos, ítems en JSON) |

El encolado es un script Lua atómico (no pisa un pedido existente). Los entregados salen del `ZSET` y el hash se conserva 24 h.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `PORT` | No (`8001`) | Puerto de escucha |
| `REDIS_QUEUE_URL` | Sí | Redis de la cola |
| `SERVICE_TOKEN` | Sí | Secreto para `POST /queue/orders` |
| `DATABASE_URL` | Etapa 3 | PostgreSQL; si está vacía se usa `DATABASE_URL_GO` |
| `RABBITMQ_URL`, `RABBITMQ_EXCHANGE` | Etapa 3 | Publicación de `order.ready` (exchange `panaderia.events`) |
| `AGING_FACTOR` | No (`0.5`) | Descuento de score por minuto de espera |
| `KITCHEN_CAPACITY` | No (`4`) | Pedidos simultáneos en preparación |
| `DEFAULT_PREP_MINUTES` | No (`5`) | Minutos si no hay tiempo base |

Ver [`.env.example`](.env.example).

## Correr y probar

```powershell
cd kitchen-service
go vet ./...
go test ./...
go build ./...
```

Las pruebas de Redis usan miniredis y no necesitan Docker. Con Docker:

```powershell
docker compose -f docker-compose.p2.yml --env-file .env.p2.example up -d --build redis postgres rabbitmq rabbitmq-init kitchen-service
```

## Estructura

```text
cmd/kitchen/main.go           arranque
internal/config               variables de entorno
internal/http                 rutas, handlers, autorización y errores
internal/queue                prioridad (priority.go), ETA (eta.go), Redis (redis_store.go), servicio
tests/                        pruebas de prioridad, ETA y handlers
```
