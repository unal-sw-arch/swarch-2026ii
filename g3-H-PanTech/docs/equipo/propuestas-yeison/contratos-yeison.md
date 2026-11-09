# Contratos propuestos por Yeison (para consolidar en `docs/equipo/contratos.md`)

Lo que **ya implementé** y lo que **necesito confirmar** con cada compañero. Los esquemas JSON de eventos están en `schemas/`
y los prueban el productor (`order-service/tests/test_contracts.py`) y el consumidor (`worker/tests/test_contracts.py`).

## Eventos (exchange `panaderia.events`, tipo topic, routing key = nombre del evento; una cola por evento, ver `infrastructure/rabbitmq/definitions.json`)

| Evento | Productor | Consumidor | Notas |
|---|---|---|---|
| `order.paid` | Order Service (outbox) | Worker | Payload mínimo + `order_type`, `table_number`, `items[]` (ver esquema) para encolar en Kitchen |
| `order.ready` | Kitchen (Axel) | Worker | `event_id`, `order_id`, `ready_at`, `occurred_at` (+ `customer_id` opcional) |
| `reservation.created` / `.cancelled` | Order Service (outbox) | Worker | Del contrato original |
| `reservation.confirmed` | Order Service (outbox) | Worker | **Nuevo, a aprobar**: requiere la cola `worker.reservation_confirmed` (ver `rabbitmq.reservation_confirmed.parche.json`). Hasta entonces no se publica (`PUBLISH_RESERVATION_CONFIRMED=false`) y `reservation_read` queda en `pendiente` tras confirmar |

Todos llevan `event_id` (UUID, también `message_id` de AMQP) y `occurred_at`. Entrega **al menos una vez**: los consumidores son idempotentes por `event_id`.

## Endpoints servicio a servicio (`X-Service-Token`)

| Llamada | Quién | Contrato |
|---|---|---|
| `POST {KITCHEN_URL}/queue/orders` | Worker → Kitchen | Body: `order_id`, `type` (`para_llevar\|en_mesa`), `table_number`, `customer_id`, `paid_at`, `items[{product_id, name, quantity, prep_minutes}]`. Debe ser **idempotente por `order_id`**; 200/201/202/409 = aceptado, 400/422 = descartar (DLQ), 5xx = reintentar |
| `POST {ORDER_SERVICE_URL}/internal/orders/{id}/status` | Worker → Order Service | Body `{"status": "en_preparacion\|listo\|entregado"}` → `{id, status, applied}`. Solo avanza; repetido o desordenado → `applied: false`. **El Gateway no debe enrutar `/internal/*`** |

## Preguntas abiertas

**Axel (Gateway / Kitchen)**
1. `order_id` es un UUID en texto (P1 usaba enteros). ¿Kitchen lo guarda como texto/UUID? Reporting compara `kitchen.order_status_events.order_id::text`.
2. ¿Kitchen publica solo `order.ready`? Para que Order Service refleje `en_preparacion` y `entregado` hacen falta eventos `order.preparing` y `order.delivered` (el endpoint interno ya los acepta; falta el handler en el Worker).
3. Reporting asume la tabla `kitchen.order_status_events(order_id, status, changed_at)` con una fila por transición (incluida `pendiente`). ¿Coincide con tu modelo?
4. Regla del Gateway para `/products/*`: hoy figura con `products:manage`, pero `GET /products` y `GET /menu` deben abrirse a cualquier usuario autenticado; solo `POST/PUT/DELETE /products` exigen `products:manage`.
5. ¿El Gateway reenvía el encabezado `Authorization`? Los servicios de lógica validan el JWT por su cuenta (RS256 + JWKS) y propagan `X-Request-Id`.

**Diego (Identity)**
6. Permisos que usan mis servicios: `orders:create_own`, `orders:create`, `orders:charge`, `orders:read_own`, `orders:read_all`, `products:manage`, `reservations:manage_own`, `reservations:manage_all`, `history:read_own`, `history:read_all`, `reports:read`. `orders:read_all` no aparece en tu matriz: ¿lo tendrá el mesero o solo supervisor/admin?
7. El `sub` se trata como texto (no asumo UUID). El `iss` debe ser `identity-service` (configurable con `JWT_ISSUER`).

**Jaime (Infraestructura)** — el detalle está en `ALINEACION-CON-INFRAESTRUCTURA.md`
8. Agregar `ORDER_SERVICE_URL` y la dependencia de `order-service` al worker, y `JWKS_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` a order-service y reporting-service.
9. Decidir cómo se descargan las exportaciones (RustFS no publica el puerto 9000) y si se agrega la cola de `reservation.confirmed`.
10. Dar `SELECT` a Reporting sobre `orders.*` y `kitchen.order_status_events` (hoy comparten el usuario `panaderia`, así que ya lo tiene).
