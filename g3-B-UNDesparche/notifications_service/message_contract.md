# Contrato de mensajes de la cola de notificaciones

Para quien implemente los productores (Events e Inventory). El servicio **notifications_service** consume la cola, elige la plantilla del correo y lo envía. Los productores solo indican **qué tipo de notificación** y **a quién**, más los datos que esa plantilla necesita.

## 1. Dónde publicar (RabbitMQ)

| Dato | Valor |
|---|---|
| Host (dentro de docker-compose) | `rabbitmq`, puerto `5672` |
| Usuario / contraseña (desarrollo) | `undesparche` / `undesparche` |
| Exchange | `notifications` (tipo `direct`, durable) |
| Routing key | `email.send` |
| Cola (la declara Notifications al arrancar) | `notifications.email` |

- El productor puede declarar el exchange (mismo tipo y durable) sin riesgo. **No declare la cola**: Notifications lo hace.
- Propiedades del mensaje: `content_type = application/json`, `delivery_mode = 2` (persistente), `message_id` igual al del cuerpo.
- Si Notifications aún no ha arrancado, la cola no existe y el mensaje no se puede enrutar. Con `mandatory=True` y confirmaciones del broker (`confirm_delivery`) el productor se entera.
- Con `pika`, la URL del vhost por defecto es `amqp://undesparche:undesparche@rabbitmq:5672/%2F`.

## 2. Estructura del mensaje (JSON, UTF-8)

```json
{
  "message_id": "9f1c2a64-7a0e-4a0b-9d1e-3c5b8f6a2e10",
  "schema_version": 1,
  "type": "event.cancelled",
  "source": "events",
  "occurred_at": "2026-11-02T10:00:00Z",
  "recipients": [
    {"email": "ana@unal.edu.co", "name": "Ana", "unsubscribe_url": "http://localhost:5172/unsubscribe?token=..."}
  ],
  "data": {
    "event_name": "Torneo de ajedrez",
    "place": "Edificio 401",
    "datetime_start": "2026-11-05T20:00:00Z"
  }
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `message_id` | Sí | Texto de 8 o más caracteres, único por mensaje (UUID v4). |
| `schema_version` | No | Entero, por defecto `1`. |
| `type` | Sí | Uno de los de la sección 3. Si es desconocido, el mensaje va a la cola de mensajes muertos. |
| `source` | Sí | `"events"` o `"inventory"`. |
| `occurred_at` | Sí | Fecha ISO-8601. |
| `recipients` | Sí | Lista con al menos 1 elemento. |
| `recipients[].email` | Sí | Correo válido. |
| `recipients[].name` | No | Si se envía, el correo saluda "Hola {name},". |
| `recipients[].unsubscribe_url` | No | Enlace personal de baja. Si viene, el correo lo muestra y agrega la cabecera `List-Unsubscribe`. |
| `data` | Sí | Objeto con los campos del tipo (sección 3). Campos extra se ignoran. Un campo obligatorio con `null` o `""` cuenta como faltante. |

Reglas generales:
- **Un mensaje, varios destinatarios:** el servicio envía un correo individual por cada elemento de `recipients` (nadie ve las direcciones de los demás). Para eventos, un solo mensaje puede llevar a todos los suscriptores, cada uno con su propio `unsubscribe_url`.
- **Fechas en `data`:** ISO-8601 con zona (`2026-11-05T20:00:00Z` o `+00:00`). Si no traen zona se asumen UTC. En el correo se muestran en hora de Bogotá.

## 3. Tipos de notificación y campos de `data`

| `type` | `source` | Cuándo | Obligatorios en `data` | Opcionales |
|---|---|---|---|---|
| `event.subscribed` | events | Alguien se suscribe a un evento | `event_name`, `place`, `datetime_start`, `datetime_end` | |
| `event.cancelled` | events | El evento pasa a cancelado | `event_name`, `place`, `datetime_start` | |
| `event.rescheduled` | events | Cambia fecha, hora o lugar | `event_name`, `place`, `datetime_start`, `datetime_end` | `previous_datetime_start`, `previous_place` |
| `event.started` | events | El evento pasa a en curso | `event_name`, `place`, `datetime_end` | |
| `event.finished` | events | El evento pasa a finalizado | `event_name` | |
| `implement.reserved` | inventory | Se crea una reserva | `implement_name`, `expires_at` | |
| `implement.reserve_expired` | inventory | La reserva vence (10 min) | `implement_name` | |
| `implement.borrowed` | inventory | Se confirma el préstamo | `implement_name`, `borrowed_at` | |
| `implement.returned` | inventory | Se registra la devolución | `implement_name`, `returned_at` | |

Para `event.rescheduled`, los campos `datetime_start`, `datetime_end` y `place` son los valores **nuevos**; los `previous_*` son los anteriores y el correo los muestra como "(antes: ...)".

Si el servicio necesita un tipo nuevo, hay que agregarlo en `notifications_service/app/catalog.py` y crear su plantilla en `app/templates/`.

## 4. Ejemplo de Inventory

```json
{
  "message_id": "0b6d8f0e-1d7c-4c0e-8a52-6f7a9e3b1c44",
  "schema_version": 1,
  "type": "implement.reserved",
  "source": "inventory",
  "occurred_at": "2026-11-02T15:00:00Z",
  "recipients": [{"email": "dvelasquezg@unal.edu.co", "name": "Daniel"}],
  "data": {"implement_name": "Mesa de ping pong", "expires_at": "2026-11-02T15:10:00Z"}
}
```

## 5. Qué pasa con el mensaje

- **Correcto:** se envía el correo y se confirma el mensaje.
- **Falla temporal** (red, límite del proveedor): se reintenta solo con los destinatarios que fallaron, hasta 5 veces con espera de 30 s.
- **Inválido** (tipo desconocido, faltan campos, JSON mal formado, correo rechazado): va a la cola `notifications.email.dead` y no se reintenta. El motivo aparece en el header `x-failure-reason`.
- Una falla de RabbitMQ o de Notifications **no debe tumbar la API** de Events o Inventory: publicar en un hilo aparte y capturar excepciones.

## 6. Cómo probar el JSON sin código

1. `http://localhost:8090/types` muestra esta misma tabla en vivo.
2. `http://localhost:8090/docs`, `POST /dev/publish`: valida el JSON contra el esquema y lo publica en la cola (requiere `DEV_ENDPOINTS=true`). Un error `422` indica qué campo está mal.
3. Consola de RabbitMQ: `http://localhost:15672` (usuario `undesparche`), pestaña Queues.

**Importante:** si `SANDBOX_TO` está vacío en `notifications_service/.env`, los correos son **reales**. Mientras se prueban los productores, hay que dejar `SANDBOX_TO` con un correo propio para que todo llegue ahí.
