# Notas de integración — Gateway, Kitchen y KDS/POS

Este documento complementa `axel-gomez.md` y registra lo que se verificó al integrar
la rama de Axel con el resto del equipo, los desajustes encontrados y las decisiones
tomadas. Es la referencia para no volver a romper el contrato entre Worker, Order
Service y Kitchen Service.

## 1. Alcance integrado

| Componente | Ubicación | Estado |
|---|---|---|
| API Gateway | `api-gateway/` | Rutas, JWT/JWKS, permisos, CORS, rate limit y proxy |
| Kitchen Service | `kitchen-service/` | Cola priorizada en Redis, outbox PostgreSQL, capacidad, aging, ETA y entrega |
| KDS/POS | `kds-pos/` | Cliente Flutter de escritorio para cocineros y despachadores |
| Migraciones | `kitchen-service/migrations/` | Esquema de cola/outbox |
| Pruebas | `api-gateway/`, `kitchen-service/tests/` | Unitarias y de handlers HTTP |
| Scripts | `api-gateway/scripts/`, `kitchen-service/scripts/` | A4.1/A4.2 documentados en el plan |

## 2. Flujo real de un pedido pagado

```text
Order Service  --order.paid--> RabbitMQ (panaderia.events)
      |                              |
      |                              +--> worker.order_paid
      |                                      |
      |                                      +--> Kitchen POST /queue/orders
      |                                      +--> read model de historial
      |                                      +--> comprobante PDF en RustFS
      |
      +--> cliente hace polling GET /orders/{id}/status

Kitchen PATCH /queue/orders/{id}/status (listo)
      |
      +--> outbox: order.ready (sobre canónico)
              |
              +--> worker.order_ready
                      |
                      +--> Order Service POST /internal/orders/{id}/status
                      +--> read model de historial
```

## 3. Desajustes encontrados y correcciones

### 3.1 Worker enviaba `type` y Kitchen esperaba `order_type`

- **Antes:** `worker/handlers.py` construía
  `{"type": event["order_type"], ...}`.
- **Ahora:** envía `{"order_type": event["order_type"], ...}`.
- **Prueba:** `worker/tests/test_order_paid.py` verifica `sent["order_type"]`.

### 3.2 Kitchen exigía UUID en `product_id` y `customer_id`

Order Service usa slugs (`p-cafe-americano`) y los pedidos de mostrador tienen
`customer_id = null`. El contrato real es:

- `order_id`: UUID obligatorio.
- `customer_id`: opcional; cadena vacía o `null` para mostrador.
- `product_id`: slug no vacío; no se exige UUID.
- `order_type`: `para_llevar` o `en_mesa`.

`kitchen-service/internal/http/handlers.go` ya no parsea UUID para producto/cliente.
La prueba `TestEnqueueAcceptsOrderServiceSlugsAndStaffOrders` cubre el caso real.

### 3.3 Kitchen publica `order.ready` con sobre y el Worker esperaba plano

Kitchen Service publica el sobre canónico:

```json
{
  "event_id": "…",
  "event_type": "order.ready",
  "version": 1,
  "occurred_at": "…",
  "producer": "kitchen-service",
  "payload": { "order_id": "…", "customer_id": "…", "ready_at": "…", "prep_minutes": 12 }
}
```

Order Service publica históricamente el payload plano con `event_id` en el nivel
superior. El Worker ahora normaliza ambos formatos (`normalize_event`) antes de
validar y fusiona `payload` con los metadatos. Esto permite desplegar sin vaciar el
outbox ni perder mensajes en vuelo.

**Decisión:** el sobre es el formato canónico para eventos nuevos; el formato plano
se mantiene como compatibilidad de lectura. No se cambió el esquema del outbox de
Kitchen para no romper sus pruebas ni los eventos ya escritos.

### 3.4 Los smoke tests de Axel usaban UUID inventados

Los scripts de aceptación de Kitchen enviaban UUID y `order_type`, por lo que no
ejercitaban el payload real del Worker. El smoke test de integración
(`scripts/smoke_test.py`) usa el flujo real: login, pedido, pago, cola, estados y
entrega. Las pruebas de Kitchen se conservan para validar su API de forma aislada.

## 4. Contrato de la API de Kitchen

| Método | Ruta | Permiso | Uso |
|---|---|---|---|
| `POST` | `/queue/orders` | `X-Service-Token` | Worker encola un pedido pagado |
| `GET` | `/queue` | `kitchen:read` | KDS/Web leen la cola priorizada |
| `PATCH` | `/queue/orders/{id}/status` | `kitchen:write` | Cocinero mueve a `en_preparacion` o `listo` |
| `POST` | `/queue/orders/{id}/deliver` | `dispatch:write` | Despachador confirma entrega |
| `GET` | `/history` | `history:read_all` | Historial de comandas (compatibilidad) |
| `GET` | `/health`, `/ready` | Público interno | Healthchecks |

`/internal/*` nunca se publica a través del Gateway.

## 5. KDS/POS de escritorio

- El cliente Flutter se conecta al API Gateway, no directamente a Kitchen.
- `kds-pos/lib/` contiene el cliente HTTP, el manejo de sesión y las pantallas de
  cocina/despacho.
- La build de Windows requiere Flutter con soporte Windows y Visual Studio C++
  Build Tools. No se pudo ejecutar en el entorno de integración (sin SDK Flutter).
- El almacenamiento seguro de tokens usa el mecanismo del sistema; su
  comportamiento no se verificó en una build Windows real.
- **Respaldo documentado:** `web-ssr` expone `/staff/kitchen` como vista de solo
  lectura de la cola. No reemplaza al KDS para cambiar estados ni para operar en
  cocina, pero permite demostrar la cola si el escritorio no está disponible.

## 6. Verificación recomendada

Con Docker y los SDK instalados:

```bash
cd g3-H-PanTech/api-gateway && go test ./...
cd ../kitchen-service && go test ./...
cd ../kds-pos && flutter analyze && flutter test
cd .. && python scripts/smoke_test.py
```

Para la integración específica Worker → Kitchen → Worker:

```bash
docker compose -f docker-compose.p2.yml --env-file .env.p2 up --build -d
python scripts/wait_for_services.ps1
python scripts/smoke_test.py
```

El smoke test verifica que el pedido aparezca en `/queue`, que `order.ready`
devuelva el estado a Order Service y que el historial se actualice.

## 7. Riesgos residuales

1. **KDS Windows sin build verificada.** Probar en una máquina con Flutter Windows.
2. **Secure storage sin probar.** Verificar expiración/refresh de tokens en el KDS.
3. **Eventos planos de Order Service.** El Worker los acepta, pero conviene migrar
   el outbox al sobre canónico en una siguiente iteración y actualizar los esquemas
   de `docs/equipo/propuestas-yeison/schemas/`.
4. **Historial de Kitchen.** La tabla de historial de comandas y el read model de
   Reporting tienen propósitos distintos; no se debe usar uno como sustituto del
   otro.
5. **Rate limit compartido.** El Gateway usa Redis; si Redis no está disponible, el
   comportamiento se degrada y debe verificarse en el entorno de despliegue.
