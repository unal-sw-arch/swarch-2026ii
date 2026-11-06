# Plan de trabajo — Yeison Camilo Aparicio Aponte

## Rol

**Pedidos, asincronía y reportes.** Soy el dueño del Order Service, del Worker asíncrono y del Reporting Service. Mi trabajo cubre el núcleo transaccional del negocio: pedidos, reservas, pagos simulados, menú, eventos de dominio, read models e historial.

| Campo | Valor |
|---|---|
| Rama personal | `g3-H/yeison-pedidos` |
| Rama de integración | `g3-H/Pantech-P2` |
| Componentes propios | `order-service/`, `worker/`, `reporting-service/` |
| Lenguajes | Python (Order y Worker), C# (Reporting) |
| Depende de | Jaime (PostgreSQL, MongoDB, Redis, RabbitMQ, MinIO), Diego (JWT y permisos), Axel (Kitchen API) |
| Bloquea a | Diego (menú, pedidos, reservas e historial), Axel (eventos para la cola), Jaime (smoke test) |

## Misión

Que un cliente pueda consultar el menú, crear un pedido, pagarlo y seguirlo; que el pago se confirme en el servidor y el pedido se encole **solo después**; que las reservas se gestionen con propiedad validada; y que el historial y los reportes se construyan sin bloquear la operación.

## Responsabilidades

1. **Order Service:** pedidos (para llevar y en mesa), reservas, pagos simulados, menú y catálogo, publicación de eventos.
2. **Propiedad de datos:** PostgreSQL como fuente de verdad y MongoDB como read model.
3. **Idempotencia:** un pago repetido no publica dos veces `order.paid`.
4. **Worker:** consumo de eventos, encolado en Kitchen, actualización de read models, comprobantes y notificaciones simuladas.
5. **Reporting Service:** historial general, historial propio del cliente, reportes y exportaciones.
6. **Documentación:** OpenAPI de cada servicio, contratos de eventos y README de cada carpeta.

## Fases y tareas

### Fase 0 — Esqueleto y contratos (primera semana)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y0.1 | Crear `order-service/` con FastAPI y estructura por capas | `main.py`, `routers/`, `services/`, `repositories/` | `GET /health` responde 200 |
| Y0.2 | Crear `worker/` con Celery | `celery_app.py`, `tasks/` | El worker arranca y se conecta a RabbitMQ |
| Y0.3 | Crear `reporting-service/` con .NET minimal API | Proyecto que compila | `GET /health` responde 200 |
| Y0.4 | Crear los tres `Dockerfile` | Imágenes multistage | Arrancan con variables de entorno |
| Y0.5 | Acordar los esquemas de eventos con Jaime y Axel | `docs/equipo/contratos.md` | Productor y consumidores usan el mismo payload |
| Y0.6 | Publicar el OpenAPI preliminar | `/docs` de Order y Reporting | Diego y Axel pueden consumirlo |

### Fase 1 — Order Service: menú y pedidos (semanas 2–3)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y1.1 | Migraciones del esquema de pedidos | `orders`, `order_items`, `payments`, `reservations` | Las tablas se crean por migración |
| Y1.2 | Catálogo y menú | `GET /menu`, `GET /products`, `GET /products/{id}` | Lee de MongoDB; precios y tiempos visibles |
| Y1.3 | CRUD de productos | `POST/PUT/DELETE /products` | Exige `products:manage`; un producto con pedidos no se borra (409) |
| Y1.4 | Crear pedido | `POST /orders` | `customer_id` sale del JWT, nunca del cuerpo |
| Y1.5 | Validación de tipo de pedido | `para_llevar` o `en_mesa` | Cliente solo `para_llevar`; personal puede ambos |
| Y1.6 | Total en el servidor | Cálculo desde precios de MongoDB | El cuerpo no puede fijar el total |
| Y1.7 | Consulta de pedidos propios | `GET /orders`, `GET /orders/{id}` | Cliente solo ve los suyos; personal autorizado ve el alcance que le corresponde |
| Y1.8 | Seguimiento por polling | `GET /orders/{id}/status` | Devuelve `pendiente`, `pagado`, `en_preparacion`, `listo` o `entregado` |
| Y1.9 | Pruebas | Unitarias e integración | Casos felices y de denegación |

**Reglas de negocio que debo respetar:**

- El pedido se encola **solo después** de confirmar el pago.
- El total se calcula siempre en el servidor, nunca se acepta del cliente.
- El `customer_id` se toma del JWT; no se confía en el cuerpo de la petición.
- El cliente solo crea pedidos `para_llevar` y solo ve los propios.
- Un pedido pagado no se puede pagar otra vez.

### Fase 2 — Pagos simulados e idempotencia (semanas 3–4)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y2.1 | Pago simulado | `POST /orders/{id}/payment` | Valida dueño, total y estado; marca pagado |
| Y2.2 | Idempotencia | Llave en Redis y estado del pago | Repetir la petición no publica dos veces `order.paid` |
| Y2.3 | Pago por personal | Mismo endpoint con `orders:charge` | `mesero`, `supervisor` o `admin` puede cobrar |
| Y2.4 | Publicación de `order.paid` | Productor RabbitMQ | Payload con `event_id`, `order_id`, `customer_id`, `total`, `paid_at`, `occurred_at` |
| Y2.5 | Outbox simple | Tabla `outbox` y reintento | Si RabbitMQ falla, el evento no se pierde |
| Y2.6 | Pruebas | Pago duplicado, monto alterado, pedido ajeno | Todos devuelven el error correcto |

### Fase 3 — Reservas (semanas 3–4)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y3.1 | Crear reserva | `POST /reservations` | Guarda `customer_id` del JWT y estado `pendiente` |
| Y3.2 | Consultar reservas | `GET /reservations`, `GET /reservations/{id}` | Cliente solo las propias; personal autorizado todas |
| Y3.3 | Cancelar reserva | `PUT /reservations/{id}` y `DELETE /reservations/{id}` | Valida propiedad y estado; publica `reservation.cancelled` |
| Y3.4 | Gestión del personal | Estados `confirmada` y `cancelada` | `mesero`, `supervisor` o `admin` |
| Y3.5 | Eventos de reserva | `reservation.created`, `reservation.cancelled` | Payload con `event_id`, `reservation_id`, `customer_id`, fecha, hora y `occurred_at` |
| Y3.6 | Pruebas | Reserva ajena, cancelación repetida, fecha inválida | Denegaciones correctas |

### Fase 4 — Worker y read models (semanas 4–5)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y4.1 | Consumidor de `order.paid` | Tarea Celery | Encola el pedido en Kitchen Service por HTTP |
| Y4.2 | Consumidor de `order.ready` | Tarea Celery | Actualiza el estado del pedido y el read model |
| Y4.3 | Consumidor de eventos de reserva | Tarea Celery | Actualiza el read model de reservas |
| Y4.4 | Reintentos y DLQ | Configuración de Celery | Un fallo se reintenta y termina en `panaderia.dlq` |
| Y4.5 | Idempotencia del consumidor | Registro por `event_id` | Un evento repetido no duplica efectos |
| Y4.6 | Comprobantes | PDF en MinIO (`receipts`) | Se genera una vez por pedido pagado |
| Y4.7 | Notificaciones simuladas | Log o correo de prueba | Queda registro de la notificación |
| Y4.8 | Read models | MongoDB: `order_history`, `reservation_read` | Consultas rápidas por `customer_id` y fecha |

### Fase 5 — Reporting Service (semanas 4–5)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y5.1 | Historial con alcance | `GET /history` | `history:read_all` ve todo; `history:read_own` solo lo propio |
| Y5.2 | Detalle de pedido | `GET /history/orders/{id}` | Dueño o `history:read_all`; valida propiedad |
| Y5.3 | Reportes | `GET /reports/sales`, `GET /reports/times` | Filtros por rango de fechas |
| Y5.4 | Exportaciones | `POST /reports/export` | Genera CSV o PDF en MinIO y devuelve la URL |
| Y5.5 | Lectura de datos | PostgreSQL y MongoDB | No escribe en las tablas de Order |
| Y5.6 | Pruebas | Permisos, filtros y exportación | Cliente no accede a `/history` |

### Fase 6 — Pruebas, documentación e integración (semana 6)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| Y6.1 | Pruebas de contrato | Esquemas de eventos y OpenAPI | Productor y consumidores compatibles |
| Y6.2 | Pruebas de idempotencia | Pago y eventos repetidos | Sin duplicados |
| Y6.3 | Documentación | README de cada servicio | Endpoints, variables y eventos documentados |
| Y6.4 | Diagramas | Secuencias de pedido y reserva al día | Coinciden con el código |
| Y6.5 | Integración con Jaime | Smoke test y revisión de PR | Flujo completo en verde |

## Endpoints que expongo

### Order Service (:8000)

| Método | Ruta | Permiso | Notas |
|---|---|---|---|
| GET | `/menu` | Autenticado | Menú desde MongoDB |
| GET | `/products` | Autenticado | Catálogo |
| POST | `/orders` | `orders:create_own` / `orders:create` | `customer_id` desde el JWT |
| GET | `/orders` | `orders:read_own` / `orders:read_all` | Alcance por rol |
| GET | `/orders/{id}` | Dueño o personal autorizado | Valida propiedad |
| GET | `/orders/{id}/status` | Dueño o personal autorizado | Polling |
| POST | `/orders/{id}/payment` | Dueño o `orders:charge` | Idempotente; publica `order.paid` |
| GET | `/reservations` | Dueño o `reservations:manage_all` | Solo las propias para el cliente |
| POST | `/reservations` | `reservations:manage_own` | Estado `pendiente` |
| PUT | `/reservations/{id}` | Dueño o `reservations:manage_all` | Cancelar o confirmar |
| DELETE | `/reservations/{id}` | Dueño o `reservations:manage_all` | Cancelar (alias de `PUT`), publica `reservation.cancelled` |
| GET/POST/PUT/DELETE | `/products` | `products:manage` | Solo admin |

### Reporting Service (:8003)

| Método | Ruta | Permiso | Notas |
|---|---|---|---|
| GET | `/history` | `history:read_own` / `history:read_all` | El cliente solo ve lo propio; supervisor y admin ven todo |
| GET | `/history/orders/{id}` | Dueño o `history:read_all` | Detalle con validación de propiedad |
| GET | `/reports/sales` | `reports:read` | Rango de fechas |
| GET | `/reports/times` | `reports:read` | Tiempos de preparación y entrega |
| POST | `/reports/export` | `reports:read` | CSV/PDF en MinIO |

## Contratos que debo respetar

### Eventos que publico

| Evento | Cuándo | Payload mínimo |
|---|---|---|
| `order.paid` | Pago confirmado | `event_id`, `order_id`, `customer_id`, `total`, `paid_at`, `occurred_at` |
| `reservation.created` | Reserva creada | `event_id`, `reservation_id`, `customer_id`, `date`, `time`, `people`, `occurred_at` |
| `reservation.cancelled` | Reserva cancelada | `event_id`, `reservation_id`, `customer_id`, `reason`, `occurred_at` |

### Eventos que consumo

| Evento | Productor | Efecto |
|---|---|---|
| `order.paid` | Yo mismo | Encolar en Kitchen y actualizar read model |
| `order.ready` | Kitchen Service | Marcar listo y actualizar read model |

### Propiedad de datos

| Almacén | Qué guardo |
|---|---|
| PostgreSQL | `orders`, `order_items`, `payments`, `reservations`, `outbox` |
| MongoDB | `menu`, `order_history`, `reservation_read` |
| Redis | Idempotencia de pagos y caché |
| MinIO | Comprobantes (`receipts`) |
| RabbitMQ | Publico y consumo eventos de dominio |

## Dependencias

| Dependo de | Para |
|---|---|
| Jaime | PostgreSQL, MongoDB, Redis, RabbitMQ, MinIO, variables y compose |
| Diego | JWT con `sub`, `roles`, `permissions` y `level` |
| Axel | `POST /queue/orders` para encolar y `order.ready` para cerrar el ciclo |

| Dependen de mí | Para |
|---|---|
| Diego | Menú, pedidos, pagos, reservas e historial propio |
| Axel | `order.paid` para encolar y pedidos listos para despacho |
| Jaime | Smoke test y datos semilla |
## Definición de terminado

- Un cliente consulta el menú, crea un pedido, paga y ve el estado por polling.
- Un pago repetido no duplica el evento ni la comanda.
- El pedido se encola solo después del pago.
- El cliente solo ve sus pedidos y reservas.
- El personal autorizado ve el historial general.
- El Worker procesa eventos, actualiza read models y genera comprobantes.
- Reporting responde con permisos correctos y exporta a MinIO.
- El PR tiene pruebas, README y OpenAPI actualizados.

## Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| Doble pago o doble encolado | Idempotencia en Redis y `event_id` en el consumidor |
| Pérdida de eventos si RabbitMQ falla | Outbox simple y reintentos con DLQ |
| Consistencia eventual entre PostgreSQL y MongoDB | Read models reconstruibles desde PostgreSQL |
| C# es nuevo para el equipo | Reporting minimal API con consultas simples |
| El Order Service concentra demasiado | Separar catálogo, pedidos y reservas en módulos internos |

## Primer PR

**`g3-H/yeison-pedidos`:** esqueleto de `order-service/` con menú, creación de pedido, total en el servidor y migraciones; `Dockerfile`; OpenAPI preliminar. Sin pagos ni Worker todavía.

## Lo que no hago

- No implemento login ni emisión de JWT; los valido.
- No implemento la cola de cocina ni el KDS; consumo la API de Kitchen.
- No expongo los servicios directamente al navegador; todo pasa por el Gateway.
- No toco los datos que pertenecen a Identity, Kitchen o al Gateway.
