# Contratos compartidos — Prototipo 2

**Versión:** 1.0
**Responsable:** Jaime Darley Angulo Tenorio
**Última actualización:** 2026-11-08

Este documento es la fuente única de verdad para puertos, autenticación, eventos y encabezados del Prototipo 2. Si un servicio necesita cambiar algo de aquí, se propone en el PR y Jaime consolida la nueva versión. Ningún servicio puede inventar un puerto, un claim o un nombre de evento distinto.

---

## 1. Puertos y URLs internas

| Componente | Puerto | URL interna | Publicado al host |
|---|---:|---|---|
| `web-ssr` | 3000 | `http://web-ssr:3000` | Sí, `:3000` |
| `api-gateway` | 8080 | `http://api-gateway:8080` | Sí, `:8080` |
| `identity-service` | 8002 | `http://identity-service:8002` | No |
| `order-service` | 8000 | `http://order-service:8000` | No |
| `kitchen-service` | 8001 | `http://kitchen-service:8001` | No |
| `reporting-service` | 8003 | `http://reporting-service:8003` | No |
| `rabbitmq` | 5672 / 15672 | `amqp://rabbitmq:5672` | Solo `:15672` (consola) |
| `postgres` | 5432 | `postgresql://postgres:5432` | No |
| `mongodb` | 27017 | `mongodb://mongodb:27017` | No |
| `redis` | 6379 | `redis://redis:6379` | No |
| `minio` | 9000 / 9001 | `http://minio:9000` | Solo `:9001` (consola) |

**Reglas:**

- Solo `web-ssr` y `api-gateway` se publican como entrada de usuario.
- Los servicios de lógica se llaman entre sí por nombre de contenedor, nunca por `localhost`.
- El KDS/POS es un cliente de escritorio y no tiene puerto en el compose.
- Los puertos internos no se cambian sin actualizar este documento, el compose y los diagramas.

### Variables de entorno por servicio

Cada servicio lee su configuración de variables. Los nombres exactos están en `.env.p2.example`.

| Servicio | Variables principales |
|---|---|
| `web-ssr` | `API_GATEWAY_URL`, `JWT_COOKIE_NAME`, `NEXT_PUBLIC_APP_NAME` |
| `api-gateway` | `PORT`, `IDENTITY_URL`, `ORDER_URL`, `KITCHEN_URL`, `REPORTING_URL`, `REDIS_URL`, `JWKS_URL`, `SERVICE_TOKEN` |
| `identity-service` | `PORT`, `DATABASE_URL`, `REDIS_URL`, `JWT_ISSUER`, `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`, `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL` |
| `order-service` | `PORT`, `DATABASE_URL`, `MONGODB_URL`, `REDIS_URL`, `RABBITMQ_URL`, `SERVICE_TOKEN` |
| `kitchen-service` | `PORT`, `DATABASE_URL`, `REDIS_URL`, `RABBITMQ_URL`, `SERVICE_TOKEN`, `AGING_FACTOR`, `KITCHEN_CAPACITY` |
| `reporting-service` | `PORT`, `DATABASE_URL`, `MONGODB_URL`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `SERVICE_TOKEN` |
| `worker` | `RABBITMQ_URL`, `KITCHEN_URL`, `MONGODB_URL`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `SERVICE_TOKEN` |

---

## 2. Contrato de autenticación (JWT)

### Emisión

- **Algoritmo:** RS256.
- **Emisor (`iss`):** `identity-service`.
- **Audiencia (`aud`):** `panaderia-express`.
- **Access token:** 15 minutos (`ACCESS_TOKEN_TTL=900`).
- **Refresh token:** 7 días (`REFRESH_TOKEN_TTL=604800`), opaco y almacenado en Redis.
- **JWKS:** `GET http://identity-service:8002/.well-known/jwks.json`.

### Claims obligatorios

| Claim | Tipo | Ejemplo | Uso |
|---|---|---|---|
| `sub` | string (UUID) | `"8f14e45f-..."` | Identidad del usuario; es el `customer_id` de pedidos y reservas |
| `email` | string | `"cliente@demo.com"` | Identificación visible |
| `roles` | array de string | `["mesero", "despachador"]` | Roles efectivos |
| `permissions` | array de string | `["orders:create", "orders:charge"]` | Unión de permisos de los roles |
| `level` | integer | `50` | Nivel efectivo, el mayor de los roles |
| `iss` | string | `"identity-service"` | Emisor |
| `iat` | integer | `1762560000` | Emisión (epoch) |
| `exp` | integer | `1762560900` | Expiración (epoch) |

### Reglas de autorización

- El **API Gateway** valida firma, `iss`, `aud` y expiración.
- El Gateway propaga la identidad a los servicios con los encabezados de la sección 3.
- Cada servicio **vuelve a validar** permiso y alcance antes de ejecutar la operación.
- El frontend puede ocultar botones, pero nunca es la única validación.
- El JWT nunca se guarda en `localStorage`; la Web SSR usa cookies `httpOnly`, `sameSite=lax` y `secure` en producción.

### Niveles y roles

| Nivel | Rol | Tipo |
|---:|---|---|
| 100 | `admin` | Empleado |
| 80 | `supervisor` | Empleado |
| 50 | `mesero` | Empleado |
| 50 | `cocinero` | Empleado |
| 50 | `despachador` | Empleado |
| 10 | `cliente` | Externo |

### Códigos de permiso

| Código | Descripción |
|---|---|
| `orders:create_own` | Crear un pedido propio (`para_llevar`) |
| `orders:read_own` | Ver los pedidos propios |
| `orders:create` | Crear pedidos de mostrador o en mesa |
| `orders:charge` | Cobrar o confirmar pago |
| `orders:read_all` | Ver todos los pedidos |
| `reservations:manage_own` | Crear, ver y cancelar reservas propias |
| `reservations:manage_all` | Gestionar todas las reservas |
| `kitchen:read` | Ver la cola de cocina |
| `kitchen:update` | Cambiar estados de preparación |
| `kitchen:deliver` | Confirmar entrega |
| `products:manage` | CRUD de productos |
| `history:read_own` | Ver el historial propio |
| `history:read_all` | Ver el historial general |
| `reports:read` | Ver y exportar reportes |
| `users:manage` | Gestionar cualquier cuenta |
| `users:manage_operational` | Gestionar cuentas operativas |
| `roles:manage` | Gestionar roles y permisos |

---

## 3. Encabezados internos

El Gateway agrega estos encabezados después de validar el JWT. Los servicios de lógica confían en ellos solo si la petición viene del Gateway o del Worker con `X-Service-Token`.

| Encabezado | Emisor | Formato | Ejemplo |
|---|---|---|---|
| `X-User-Id` | Gateway | UUID | `8f14e45f-...` |
| `X-User-Email` | Gateway | string | `cliente@demo.com` |
| `X-Roles` | Gateway | CSV sin espacios | `mesero,despachador` |
| `X-Permissions` | Gateway | CSV sin espacios | `orders:create,orders:charge` |
| `X-Level` | Gateway | entero | `50` |
| `X-Request-Id` | Gateway | UUID | `c2f1...` |
| `X-Service-Token` | Worker / servicios | string secreto | valor de `SERVICE_TOKEN` |

**Reglas:**

- Si falta `X-User-Id` en una ruta protegida, el servicio responde `401`.
- Si el permiso no está en `X-Permissions`, responde `403`.
- El Worker usa `X-Service-Token` para llamar a Kitchen Service; no simula un usuario.
- `X-Request-Id` se propaga a los logs de todos los servicios.

---

## 4. Contrato de eventos

### Sobre (envelope) obligatorio

Todos los eventos se publican en el exchange `panaderia.events` (tipo `topic`) y llevan este sobre:

```json
{
  "event_id": "9b2f4c1e-6a3d-4c8e-9f10-2b7d5a6e8c11",
  "event_type": "order.paid",
  "version": 1,
  "occurred_at": "2026-11-08T14:32:10Z",
  "producer": "order-service",
  "payload": {}
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `event_id` | UUID | Único; se usa para idempotencia en el consumidor |
| `event_type` | string | Igual al routing key |
| `version` | integer | Empieza en `1`; se incrementa si cambia el payload |
| `occurred_at` | ISO-8601 UTC | Momento del hecho |
| `producer` | string | Nombre del servicio productor |
| `payload` | objeto | Datos del evento |

### Eventos y routing keys

| Evento | Routing key | Productor | Consumidores | Cola |
|---|---|---|---|---|
| Pedido pagado | `order.paid` | Order Service | Worker | `worker.order_paid` |
| Pedido listo | `order.ready` | Kitchen Service | Worker, Reporting | `worker.order_ready`, `reporting.order_ready` |
| Reserva creada | `reservation.created` | Order Service | Worker | `worker.reservation_created` |
| Reserva cancelada | `reservation.cancelled` | Order Service | Worker | `worker.reservation_cancelled` |

### Payloads

**`order.paid`**

```json
{
  "order_id": "UUID",
  "customer_id": "UUID",
  "order_type": "para_llevar",
  "total": 18500,
  "currency": "COP",
  "paid_at": "2026-11-08T14:32:10Z",
  "payment_method": "simulado_tarjeta"
}
```

**`order.ready`**

```json
{
  "order_id": "UUID",
  "customer_id": "UUID",
  "ready_at": "2026-11-08T14:45:00Z",
  "prep_minutes": 12
}
```

**`reservation.created`**

```json
{
  "reservation_id": "UUID",
  "customer_id": "UUID",
  "date": "2026-11-10",
  "time": "19:00",
  "people": 4,
  "status": "pendiente"
}
```

**`reservation.cancelled`**

```json
{
  "reservation_id": "UUID",
  "customer_id": "UUID",
  "reason": "cambio_de_planes",
  "cancelled_at": "2026-11-09T10:15:00Z"
}
```

### Reglas de mensajería

- Exchange: `panaderia.events`, tipo `topic`, durable.
- Dead letter exchange: `panaderia.dlx`; cola: `panaderia.dlq`.
- Los consumidores son idempotentes por `event_id`.
- Un fallo se reintenta con backoff exponencial y luego va a la DLQ.
- Los eventos no llevan datos sensibles: ni contraseñas, ni tokens, ni datos de tarjeta.
- Order Service usa un outbox simple para no perder eventos si RabbitMQ falla.

---

## 5. Formato uniforme de errores

Todas las APIs responden con el mismo cuerpo:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "El pedido no existe o no pertenece al usuario",
  "request_id": "c2f1...",
  "details": {}
}
```

| HTTP | `code` de ejemplo | Cuándo |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo o parámetros inválidos |
| 401 | `UNAUTHENTICATED` | Falta el token o está vencido |
| 403 | `FORBIDDEN` | El rol o permiso no alcanza |
| 404 | `NOT_FOUND` | El recurso no existe o no es del usuario |
| 409 | `INVALID_STATE` | Transición o estado incompatible |
| 429 | `RATE_LIMITED` | Se superó el límite |
| 500 | `INTERNAL_ERROR` | Error no controlado |

**Regla de privacidad:** cuando un cliente pide un recurso ajeno, se responde `404` y no `403`, para no revelar que el recurso existe.

---

## 6. Estados del dominio

### Pedido

```text
pendiente -> pagado -> en_preparacion -> listo -> entregado
     |           |
     +-----------+--> cancelado
```

| Estado | Significado |
|---|---|
| `pendiente` | Creado, sin pago confirmado |
| `pagado` | Pago confirmado; el Worker puede encolar |
| `en_preparacion` | Kitchen lo tomó |
| `listo` | Terminado, pendiente de entrega |
| `entregado` | Despachado |
| `cancelado` | Cancelado antes de entregar |

**Regla clave:** el pedido se encola en Kitchen **solo** después de `pagado`.

### Reserva

```text
pendiente -> confirmada -> cancelada
     |
     +--> cancelada
```

### Transiciones de cocina

```text
pendiente -> en_preparacion -> listo -> entregado
```

Una transición inválida devuelve `409 INVALID_STATE`.

---

## 7. Propiedad de datos

| Servicio | PostgreSQL | MongoDB | Redis | MinIO |
|---|---|---|---|---|
| Identity | `auth.*` | — | refresh tokens | — |
| Order | `orders.*`, `payments.*`, `reservations.*`, `outbox` | `menu`, `order_history`, `reservation_read` | idempotencia de pagos | — |
| Kitchen | `kitchen.*` | — | cola priorizada y ETA | — |
| Reporting | lectura de `orders.*` | `report_snapshots` | — | exportaciones |
| Gateway | — | — | rate limiting y caché de sesión | — |
| Worker | — | `order_history`, `reservation_read` | — | `receipts` |

**Reglas:**

- Cada servicio es dueño de su esquema. Nadie escribe en las tablas de otro.
- Reporting solo lee.
- PostgreSQL es la fuente de verdad transaccional; MongoDB contiene read models reconstruibles.
- La consistencia entre PostgreSQL y MongoDB es eventual.

---

## 8. Convenciones de nombres

- **Servicios y contenedores:** `kebab-case` (`order-service`).
- **Rutas REST:** plural, `kebab-case` (`/queue/orders`, `/reports/sales`).
- **Campos JSON:** `snake_case` (`order_id`, `customer_id`, `created_at`).
- **Tablas y columnas SQL:** `snake_case` en plural para tablas.
- **Variables de entorno:** `UPPER_SNAKE_CASE`.
- **Eventos:** `dominio.accion` en pasado (`order.paid`).
- **Permisos:** `recurso:accion` (`orders:create`).

---

## 9. Política de cambios

1. Cualquier cambio a este documento se propone en el PR del servicio que lo necesita.
2. Jaime revisa el impacto en los otros servicios antes de mergear.
3. Si cambia un contrato, se actualizan en el mismo PR:
   - este documento,
   - `.env.p2.example`,
   - `docker-compose.p2.yml` si cambia un puerto o una variable,
   - el diagrama C&C o de despliegue si cambia una relación,
   - la matriz NFR del README si cambia el alcance.
4. Los contratos se versionan en la cabecera del documento.
