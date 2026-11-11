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

Las pruebas de integración levantan PostgreSQL y Redis con Docker y **se saltan solas** si Docker no está disponible (o con `go test -short ./...`). Las demás usan miniredis y no necesitan Docker. `tests/openapi_test.go` valida `openapi.yaml` y comprueba que las respuestas reales cumplen el contrato.

Con Docker Compose:

```powershell
docker compose -f docker-compose.p2.yml --env-file .env.p2.example up -d --build postgres redis rabbitmq rabbitmq-init kitchen-service
```

## Pruebas de humo, seguridad y carga

Corren contra el stack real (Docker). Desde `g3-H-PanTech`:

```powershell
.\kitchen-service\tests\smoke\run-smoke.ps1                  # humo + seguridad (96 verificaciones)
.\kitchen-service\tests\smoke\run-smoke.ps1 -Suite security  # solo seguridad
.\kitchen-service\tests\load\run-load.ps1                    # 50 pedidos, 500 consultas a GET /queue
```

Los scripts levantan postgres, redis, rabbitmq, kitchen-service y api-gateway (con `--no-deps`: Identity, Order y Reporting aún no existen) en el proyecto `panaderia-smoke`, con `tests/smoke/docker-compose.smoke.yml`, y lo bajan con `down -v` al terminar (`-KeepUp` lo deja arriba). Los contenedores tienen nombre fijo: baje el stack de desarrollo antes. El override:

- sirve el JWKS desde el proceso de prueba (`go run ./tests/smoke`), que firma los tokens; **el helper nunca va en la imagen** (`.dockerignore` excluye `tests/`);
- publica Kitchen en `127.0.0.1:8001` solo para probar llamadas directas;
- usa un Redis con contraseña porque `infrastructure/redis/redis.conf` tiene `protected-mode yes` sin contraseña y ningún servicio puede conectarse (ver `docs/equipo/axel-notas-integracion.md`, X11).

**Humo:** salud, 401 sin token, ruteo, encolar con `X-Service-Token` (201, 200 idempotente, 400), SJF, ETA, estados y `409`, capacidad (`KITCHEN_AT_CAPACITY`), entrega, `order.ready` en `worker.order_ready` (sobre y payload) e historial en PostgreSQL.

**Seguridad:** tokens ajenos o inválidos (clave ajena, `iss`, `aud`, vencido, sin `exp`, `sub` no UUID, HS256, `alg none`, inyección CSV en permisos), rol insuficiente (cliente, mesero, cocinero, despachador, supervisor), recurso ajeno (`404`), Kitchen directo sin `X-Service-Token`, falsificación de `X-*` a través del Gateway, rate limiting (`429`) y CORS.

**Carga:** `go run ./tests/load` encola 50 pedidos (idempotente) y consulta `GET /queue` con 10 clientes; verifica posiciones consecutivas, orden por `priority_score` con desempate FIFO y p95 bajo 500 ms. Resultado de referencia en esta máquina: 500 consultas, p95 ≈ 8 ms, 0 respuestas malas. Con `-gateway URL -token JWT` consulta por el Gateway.

### Límite de confianza

Kitchen **confía en `X-User-Id` y `X-Permissions`**: la frontera es la red interna (no se publica en el compose) y el Gateway, que borra los `X-*` del cliente y los reescribe desde el JWT. Quien llegue a Kitchen directo desde la red puede falsificarlos; solo `POST /queue/orders` exige un secreto (`X-Service-Token`). Para un entorno real haría falta mTLS o validar el JWT también en Kitchen.

## OpenAPI

[`openapi.yaml`](openapi.yaml) (OpenAPI 3.0.3) documenta los siete endpoints, esquemas, ejemplos y errores. Se puede abrir en cualquier visor (por ejemplo Swagger Editor).

## Estructura

```text
cmd/kitchen/main.go           arranque
internal/config               variables de entorno
internal/http                 rutas, handlers, autorización y errores
internal/queue                prioridad, capacidad, ETA, transiciones (Redis) y servicio
internal/repository           PostgreSQL: migraciones, tiempos, historial y capacidad
internal/events               publicador RabbitMQ y outbox
migrations/                   SQL del esquema kitchen
openapi.yaml                  contrato de la API
tests/                        prioridad, ETA, handlers, transiciones, eventos, integración y contrato OpenAPI
tests/smoke/                  humo y seguridad contra el stack (go run ./tests/smoke) y run-smoke.ps1
tests/load/                   carga ligera de GET /queue y run-load.ps1
```
