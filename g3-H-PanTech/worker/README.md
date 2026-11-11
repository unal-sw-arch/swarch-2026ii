# Worker

Procesos asíncronos de Panadería Express: Python 3.12 + Celery sobre RabbitMQ. No expone HTTP.

## Qué hace

```
RabbitMQ  panaderia.events ──► worker.order_paid / order_ready / reservation_* ──► (bootstep) ──► tarea Celery ──► reintentos ──► panaderia.dlx ──► panaderia.dlq
```

El Worker lee los eventos de dominio de las colas de `infrastructure/rabbitmq/definitions.json` (una por evento, enlazadas a `panaderia.events`) y convierte cada uno
en una tarea Celery, que aporta reintentos con backoff. El mensaje de dominio se confirma solo después de publicar la tarea.

| Evento | Productor | Efecto |
|---|---|---|
| `order.paid` | Order Service | Encola el pedido en Kitchen (`POST /queue/orders`), actualiza `order_history`, registra la notificación y pide el comprobante |
| `order.ready` | Kitchen Service | Marca el pedido `listo` en Order Service (`/internal/orders/{id}/status`) y en `order_history` |
| `reservation.created` / `.confirmed` / `.cancelled` | Order Service | Actualiza `reservation_read` y registra la notificación |

Tarea adicional `generate_receipt`: genera un PDF en RustFS/S3 (bucket `receipts`, objeto `{order_id}.pdf`) **una sola vez por pedido**.

> El pedido solo llega a cocina después del pago porque el único camino hacia Kitchen es el evento `order.paid`.

## Fiabilidad

- **Reintentos:** todo error se reintenta con backoff exponencial (2, 4, 8… máx. 60 s, con jitter) hasta `MAX_RETRIES` (5).
  Un evento inválido (`InvalidEvent`) no se reintenta.
- **DLQ:** al agotar los reintentos, o ante un evento inválido, desconocido o ilegible, el mensaje se publica en `panaderia.dlq`
  con las cabeceras `x-original-routing-key` y `x-error`.
- **Idempotencia:** cada handler guarda `event_id` en `processed_events` (índice único `event_id + handler`); un evento repetido se ignora.
  Si un handler falla a medias se repite completo, y por eso cada paso es idempotente: Kitchen lo es por `order_id`, los read models son
  upserts que nunca retroceden un estado, el comprobante se crea solo si no existe y la notificación es única por evento y tipo.
- **Entrega al menos una vez:** `acks_late` + `reject_on_worker_lost`; si el proceso muere, el mensaje se reentrega.

## Datos que escribe (MongoDB)

`order_history` (con `created_at` = `paid_at`), `reservation_read` (read models; reconstruibles desde PostgreSQL), `processed_events`, `notifications` (log simulado).

## Variables de entorno

Mismos nombres que `docker-compose.p2.yml` (ver `.env.example`): `RABBITMQ_URL`/`CELERY_BROKER_URL`, `RABBITMQ_EXCHANGE`, `RABBITMQ_DLX`, `RABBITMQ_DLQ`,
`KITCHEN_URL`, `ORDER_SERVICE_URL`, `SERVICE_TOKEN`, `MONGODB_URL`, `S3_*` (RustFS), `WORKER_MAX_RETRIES`, `WORKER_CONCURRENCY`.
`EVENTS_QUEUES` lista las colas a consumir (por defecto las cuatro de `definitions.json`; agrega `worker.reservation_confirmed` cuando exista).
`DECLARE_TOPOLOGY=false` por defecto: las colas las crea `rabbitmq-init`; con `true` el Worker las declara con los mismos argumentos.

## Correr y probar

```bash
pip install -r requirements.txt
celery -A celery_app worker --loglevel=INFO

python -m unittest discover -s tests -t .   # o pytest; la lógica se prueba con dependencias falsas
```
Las pruebas de configuración de Celery y de los clientes HTTP se omiten si `celery` o `httpx` no están instalados.
