# Order Service

Menú y catálogo, pedidos, pagos simulados e idempotentes y reservas de Panadería Express.
Python 3.12 + FastAPI, puerto **8000**. Los usuarios llegan por el API Gateway (`/menu`, `/products`,
`/orders`, `/reservations`); `/internal/*` es solo servicio a servicio y el Gateway **no** lo enruta.
Este servicio **valida** JWT, no los emite.

OpenAPI: `http://order-service:8000/docs` · `/openapi.json`.

## Endpoints

| Método | Ruta | Permiso | Notas |
|---|---|---|---|
| GET | `/health` | — | Comprueba PostgreSQL |
| GET | `/menu` | autenticado | Productos activos (MongoDB) con precio y `prep_minutes` |
| GET | `/products`, `/products/{id}` | autenticado | Los inactivos solo los ve `products:manage` |
| POST/PUT/DELETE | `/products` | `products:manage` | `DELETE` → 409 si el producto ya tiene pedidos |
| POST | `/orders` | `orders:create_own` / `orders:create` | `customer_id` del JWT; total calculado en el servidor |
| GET | `/orders` | `orders:read_own` / `orders:read_all` | Cliente: solo los propios. `?status=&limit=&offset=` |
| GET | `/orders/{id}` · `/orders/{id}/status` | ídem | Pedido ajeno → 404 |
| POST | `/orders/{id}/payment` | `orders:create_own` (dueño) / `orders:charge` | Header opcional `Idempotency-Key` |
| POST/GET | `/reservations`, `/reservations/{id}` | `reservations:manage_own` / `reservations:manage_all` | Cliente: solo las propias |
| PUT | `/reservations/{id}` | ídem | `{"status": "cancelada"\|"confirmada", "reason"?}`; confirmar solo el personal |
| DELETE | `/reservations/{id}` | ídem | Alias de cancelar |
| POST | `/internal/orders/{id}/status` | `X-Service-Token` | Lo usa el Worker al consumir `order.ready` |

Errores: `{"code", "message", "request_id", "details"?}` y encabezado `X-Request-Id` (se propaga el que llega del Gateway).

## Reglas de negocio

- `POST /orders` **no acepta** `total`, `customer_id` ni `status` (422). El total sale de los precios del catálogo.
- Cliente: solo `para_llevar`. Personal (`orders:create`): `para_llevar` o `en_mesa` (exige `table_number`); queda con `customer_id = null` y `created_by` = su `sub`.
- Un pedido nace `pendiente` y se encola en cocina **solo después del pago** (Worker, vía `order.paid`).
- **Pago:** `amount` debe igualar el total (si no, 422). Un pedido se paga una vez (`payments.order_id` único + bloqueo de fila).
  Repetir con la **misma** `Idempotency-Key` devuelve `200` con el pago original y no publica otro evento; sin llave, o con otra, `409`.
  Redis acelera la repetición y evita pagos simultáneos; si Redis cae, PostgreSQL sigue garantizando la unicidad.
- **Reservas:** fecha/hora futuras (zona `BUSINESS_TZ`), máximo `MAX_RESERVATION_PEOPLE` personas. Cancelar dos veces → 409.
- **Estado por cocina:** `/internal/orders/{id}/status` solo avanza (`pagado → en_preparacion → listo → entregado`); un evento repetido o desordenado responde `applied: false`.

## Eventos (exchange `panaderia.events`, tipo topic; routing key = nombre del evento)

Se escriben en la tabla `outbox` en la **misma transacción** del cambio y un relay los publica (con confirmación del
broker y `mandatory`). Si RabbitMQ falla, quedan pendientes y se reintentan con backoff (máx. 60 s).
Todos llevan `event_id` (= id de la fila del outbox, para idempotencia) y `occurred_at`.

| Evento | Payload |
|---|---|
| `order.paid` | `event_id`, `order_id`, `customer_id`, `total`, `paid_at`, `occurred_at` + extras: `order_type`, `table_number`, `items[{product_id, name, quantity, prep_minutes, unit_price}]` |
| `reservation.created` | `event_id`, `reservation_id`, `customer_id`, `date`, `time`, `people`, `occurred_at` |
| `reservation.cancelled` | ídem + `reason` |
| `reservation.confirmed` *(propuesto; desactivado por defecto)* | ídem que `created`. Necesita la cola `worker.reservation_confirmed` |

## Datos

| Almacén | Uso |
|---|---|
| PostgreSQL, esquema `orders` | `orders`, `order_items`, `payments`, `reservations`, `outbox` (migraciones Alembic `0001`–`0003`) |
| MongoDB | `menu` (catálogo, campo `available`; lo siembra el servicio si está vacío), y los read models `order_history` y `reservation_read` que escribe el Worker |
| Redis | `idem:payment:{order}:{key}` y `lock:payment:{order}` |
| RabbitMQ | publica los eventos de arriba |

Documento de `menu`: `{"product_id", "name", "category": "vitrina|preparado", "price": Decimal128, "prep_minutes", "available"}` (la API lo expone como `active`).
Índices: `migrations/mongo/001_indexes.js` (también se aplican al arrancar).

## Variables de entorno

Mismos nombres que `docker-compose.p2.yml` (ver `.env.example`). Obligatoria: `SERVICE_TOKEN`.
`DATABASE_URL` acepta `postgresql+psycopg2://...` (se normaliza al driver psycopg 3). `MONGODB_URL` incluye credenciales y la base en la ruta.
JWT: `JWKS_URL` (por defecto el de identity-service), `JWT_ISSUER` y `JWT_AUDIENCE` (por defecto `panaderia-express`); `JWT_PUBLIC_KEY_FILE`/`JWT_PUBLIC_KEY` tienen prioridad (desarrollo).
Otras: `REDIS_URL`, `RABBITMQ_URL`, `RABBITMQ_EXCHANGE`, `PORT`, `BUSINESS_TZ`, `SEED_MENU`, `OUTBOX_RELAY_ENABLED`, `OUTBOX_POLL_SECONDS`,
`PUBLISH_RESERVATION_CONFIRMED` (false hasta que exista la cola `worker.reservation_confirmed`).

## Correr y probar

```bash
pip install -r requirements-dev.txt
pytest          # SQLite en memoria, catálogo y Redis falsos, JWT RS256 con clave generada: no necesita infraestructura

docker build -t order-service .   # el entrypoint aplica `alembic upgrade head` y levanta Uvicorn
```
