# Documentación — Notifications Service

UNdesparche · Arquitectura de Software · Versión 1.0 · Noviembre de 2026

## 1. Descripción general

**Notifications Service** envía los correos electrónicos de UNdesparche: confirmaciones de suscripción a eventos, cambios de horario o lugar, cancelaciones, reservas de implementos, préstamos y devoluciones. Corresponde al componente `«background» Notifications` del diagrama de componentes y conectores: es el **consumidor** de la `NotificationsQueue` dentro de un patrón **Productor-Consumidor**, con **RabbitMQ** como broker.

Events e Inventory (productores) publican un mensaje que indica *qué tipo* de notificación y *a quiénes*. Notifications elige la plantilla HTML, genera un correo personalizado por destinatario y lo entrega a un proveedor externo (**Brevo**, con **SMTP** como respaldo).

Está implementado con **FastAPI**, **aio-pika** (AMQP asíncrono), **Jinja2** (plantillas), **httpx** (API de Brevo) y **aiosmtplib** (SMTP). FastAPI aloja el consumidor en su ciclo de vida (`lifespan`) y expone únicamente endpoints operativos. **No existen endpoints de negocio**: el contrato de entrada es el mensaje AMQP.

> **Base URL:** `http://localhost:8090` cuando se levanta con `docker compose`. Los productores no usan esta URL: publican directamente en RabbitMQ.

### Atributos de calidad que cubre

| Atributo | Cómo se logra |
| --- | --- |
| Asincronismo | La API de Events/Inventory solo encola el mensaje y responde; el envío ocurre en otro proceso. |
| Desacoplamiento | Los productores no conocen el proveedor de correo ni las plantillas. |
| Tolerancia a fallos | Reintentos con espera, cola de mensajes muertos y reintento solo de los destinatarios fallidos. |
| Personalización | Un correo individual por destinatario, con su nombre y su enlace de baja. |

### Decisión de diseño: proveedor de correo

Se evaluó **Resend** inicialmente, pero sin un dominio verificado por DNS solo permite enviar al correo dueño de la cuenta, lo que impedía demostrar envíos a varios usuarios. Se reemplazó por **Brevo**, que verifica el remitente mediante un enlace enviado a su correo y no exige DNS. El componente externo `Resend` del diagrama debe actualizarse a `Brevo`.

## 2. Arquitectura y flujo

| Elemento | Rol |
| --- | --- |
| Events Service, Inventory Service | Productores. Publican en el exchange `notifications` (conector AMQP). |
| NotificationsQueue (RabbitMQ) | Broker. Cola durable `notifications.email`. |
| Notifications Service | Consumidor. Valida, renderiza y envía. |
| Brevo / SMTP | Servicio externo que entrega el correo. |

Flujo de una notificación:

1. Ocurre una acción en la web (por ejemplo, el organizador cambia la fecha de un evento) y la API correspondiente la procesa.
2. El productor construye el mensaje (tipo, destinatarios y datos tomados de su base de datos) y lo publica en RabbitMQ en un hilo aparte, sin bloquear la respuesta HTTP.
3. Notifications recibe el mensaje y lo valida contra el esquema y el catálogo de tipos.
4. Renderiza la plantilla y el asunto para cada destinatario.
5. Envía un correo por destinatario al proveedor (hasta 5 envíos en paralelo).
6. Confirma el mensaje (`ack`). Si algo falla, aplica la política de la sección 7.

El navegador **nunca** habla con Notifications ni decide destinatarios: el frontend solo invoca a Events e Inventory. Así nadie puede usar el sistema para enviar correos arbitrarios.

## 3. Topología de RabbitMQ

Notifications declara toda la topología al arrancar.

| Elemento | Nombre | Detalle |
| --- | --- | --- |
| Exchange | `notifications` | `direct`, durable. Los productores publican aquí. |
| Routing key de envío | `email.send` | Enlaza el exchange con la cola principal. |
| Cola principal | `notifications.email` | Durable. Los mensajes rechazados van al exchange de mensajes muertos. |
| Cola de espera | `notifications.email.retry` | Routing key `email.retry`. TTL igual a `RETRY_DELAY_MS`; al vencer, el mensaje vuelve a `email.send`. |
| Exchange de mensajes muertos | `notifications.dlx` | `direct`, durable. |
| Cola de mensajes muertos (DLQ) | `notifications.email.dead` | Routing key `email.dead`. Aquí quedan los mensajes que no se pudieron entregar. |

Los argumentos de una cola no se pueden modificar una vez creada. Si se cambia `RETRY_DELAY_MS`, hay que borrar la cola `notifications.email.retry` desde la consola de RabbitMQ para que se vuelva a declarar.

Se consume con `prefetch` (por defecto 10). La confirmación (`ack`) se hace después de publicar el reintento o el mensaje muerto, por lo que la entrega es *al menos una vez*.

## 4. Contrato del mensaje

Cuerpo JSON en UTF-8, con propiedades AMQP `content_type = application/json`, `delivery_mode = 2` (persistente) y `message_id` igual al del cuerpo.

```json
{
  "message_id": "9f1c2a64-7a0e-4a0b-9d1e-3c5b8f6a2e10",
  "schema_version": 1,
  "type": "event.rescheduled",
  "source": "events",
  "occurred_at": "2026-11-02T10:00:00Z",
  "recipients": [
    {"email": "ana@unal.edu.co", "name": "Ana", "unsubscribe_url": "http://localhost:5172/unsubscribe?token=..."}
  ],
  "data": {
    "event_name": "Torneo de ajedrez",
    "place": "Edificio 401",
    "datetime_start": "2026-11-05T20:00:00Z",
    "datetime_end": "2026-11-05T23:00:00Z",
    "previous_datetime_start": "2026-11-04T20:00:00Z",
    "previous_place": "Plaza Che"
  }
}
```

| Campo | Obligatorio | Regla |
| --- | --- | --- |
| `message_id` | Sí | Texto de 8 o más caracteres, único por mensaje (se recomienda UUID v4). |
| `schema_version` | No | Entero; por defecto `1`. |
| `type` | Sí | Uno de los tipos de la sección 5. |
| `source` | Sí | `events` o `inventory`. |
| `occurred_at` | Sí | Fecha ISO-8601. |
| `recipients` | Sí | Lista con al menos un elemento. |
| `recipients[].email` | Sí | Correo con formato válido. |
| `recipients[].name` | No | Si viene, el correo saluda con el nombre. |
| `recipients[].unsubscribe_url` | No | Enlace personal de baja. Si viene, el correo lo muestra y añade la cabecera `List-Unsubscribe`. |
| `data` | Sí | Objeto con los campos del tipo. Los campos adicionales se ignoran; un campo obligatorio con `null` o cadena vacía cuenta como faltante. |

Reglas generales:

- Un mensaje puede llevar muchos destinatarios; el servicio envía **un correo individual por cada uno**, de modo que nadie ve las direcciones de los demás.
- Las fechas dentro de `data` se envían en ISO-8601 con zona horaria. Si no traen zona, se asumen UTC. En el correo se muestran en **hora de Bogotá**, por ejemplo `jue 5 nov 2026, 3:00 p. m.`.
- Los valores de `data` se escapan como HTML en las plantillas; el asunto se genera como texto plano.

## 5. Catálogo de notificaciones

| `type` | Origen | Cuándo se envía | Asunto | Obligatorios en `data` | Opcionales |
| --- | --- | --- | --- | --- | --- |
| `event.subscribed` | events | Alguien se suscribe a un evento | Te suscribiste a {event\_name} | `event_name`, `place`, `datetime_start`, `datetime_end` |  |
| `event.cancelled` | events | El evento pasa a cancelado | Evento cancelado: {event\_name} | `event_name`, `place`, `datetime_start` |  |
| `event.rescheduled` | events | Cambia fecha, hora o lugar | Cambios en {event\_name} | `event_name`, `place`, `datetime_start`, `datetime_end` | `previous_datetime_start`, `previous_place` |
| `event.started` | events | El evento pasa a en curso | {event\_name} acaba de comenzar | `event_name`, `place`, `datetime_end` |  |
| `event.finished` | events | El evento pasa a finalizado | {event\_name} ha finalizado | `event_name` |  |
| `implement.reserved` | inventory | Se crea una reserva | Reservaste {implement\_name} | `implement_name`, `expires_at` |  |
| `implement.reserve_expired` | inventory | La reserva de 10 minutos vence | Tu reserva de {implement\_name} venció | `implement_name` |  |
| `implement.borrowed` | inventory | Se confirma el préstamo | Préstamo registrado: {implement\_name} | `implement_name`, `borrowed_at` |  |
| `implement.returned` | inventory | Se registra la devolución | Devolución registrada: {implement\_name} | `implement_name`, `returned_at` |  |

En `event.rescheduled`, `datetime_start`, `datetime_end` y `place` son los valores **nuevos**; los `previous_*` son los anteriores y el correo los muestra como «antes: ...».

**Agregar un tipo nuevo:** registrarlo en `app/catalog.py` (plantilla, asunto y campos obligatorios) y crear su archivo en `app/templates/`, que extiende `base.html`.

## 6. Endpoints HTTP

Solo son operativos. No requieren autenticación.

| Método | Ruta | Descripción | Disponibilidad |
| --- | --- | --- | --- |
| `GET` | `/health` | Estado del servicio y de la conexión a RabbitMQ. | Siempre |
| `GET` | `/types` | Contrato en vivo: tipos y campos obligatorios. | Siempre |
| `POST` | `/dev/publish` | Publica un mensaje en la cola (pruebas). | Solo con `DEV_ENDPOINTS=true` |
| `GET` | `/docs` | Swagger de FastAPI. | Siempre |

### 6.1. Estado del servicio

**`GET /health`**

Respuesta `200 OK`:

```json
{"status": "ok", "rabbitmq": true, "provider": "brevo", "email_mode": "live"}
```

`email_mode` puede ser `dry-run`, `sandbox` o `live` (sección 8). Si el consumidor no está conectado a RabbitMQ responde `503 Service Unavailable` con el mismo cuerpo bajo la clave `detail` y `status: degraded`.

### 6.2. Contrato en vivo

**`GET /types`**

Respuesta `200 OK` con `exchange`, `routing_key`, `queue`, `dead_letter_queue` y, en `types`, un objeto que asocia cada `type` con su `source` y la lista `required_data`.

### 6.3. Publicar un mensaje de prueba

**`POST /dev/publish`**

Recibe un mensaje completo (sección 4), lo valida contra el esquema y lo publica en el exchange como lo haría un productor. Sirve para probar plantillas y proveedor sin depender de Events ni de Inventory.

Respuestas:

- `202 Accepted`: `{"queued": "<message_id>"}`.
- `422 Unprocessable Content`: el cuerpo no cumple el esquema (por ejemplo `message_id` demasiado corto o JSON mal formado). La respuesta indica el campo.
- `503 Service Unavailable`: RabbitMQ no está disponible.

La validación del esquema no comprueba el tipo ni los campos de `data`: eso ocurre en el consumidor. Un tipo desconocido o datos faltantes devuelven `202` y el mensaje termina en la cola de mensajes muertos (se ve en el log).

Este endpoint **no tiene autenticación**; debe desactivarse fuera de desarrollo.

## 7. Procesamiento y manejo de fallos

El consumidor clasifica cada resultado:

| Situación | Resultado |
| --- | --- |
| Todos los destinatarios enviados | `ack`. |
| JSON mal formado, esquema inválido, tipo desconocido, datos faltantes o error de plantilla | El mensaje se rechaza (`reject` sin reencolar) y va a la cola de mensajes muertos. No se reintenta. |
| Falla **temporal** de un destinatario (red, límite de frecuencia, error 5xx, SMTP 4xx) | Se republica en la cola de espera **solo con los destinatarios que fallaron**, con el encabezado `x-retry-count` incrementado. |
| Falla **permanente** de un destinatario (dirección rechazada, remitente no verificado, credenciales inválidas) | Se publica en la cola de mensajes muertos solo con esos destinatarios, con el motivo en el encabezado `x-failure-reason`. Los demás destinatarios no se ven afectados. |
| Reintentos agotados (`MAX_RETRIES`) | Se publica en la cola de mensajes muertos con el último motivo. |
| Error inesperado al procesar | Se trata como temporal. |

Los reintentos usan una **espera fija** (`RETRY_DELAY_MS`, 30 s por defecto), no exponencial. Los mensajes de la cola de mensajes muertos se pueden inspeccionar y republicar manualmente desde la consola de RabbitMQ.

## 8. Proveedores de correo y modos de envío

### 8.1. Brevo (principal)

Se selecciona con `MAIL_PROVIDER=brevo`. Cada correo se envía con `POST https://api.brevo.com/v3/smtp/email` y el encabezado `api-key`.

- Respuestas `200`, `201` o `202`: correo aceptado.
- `429` y `5xx`: falla temporal.
- Cualquier otro `4xx`: falla permanente.

Preparación: crear la cuenta, verificar el remitente en *Senders, domains & dedicated IPs → Senders* (llega un enlace al correo del remitente), generar la clave en *SMTP & API → API Keys* y colocar en `MAIL_FROM_EMAIL` exactamente la dirección verificada. El plan gratuito permite 300 correos por día, y cada destinatario cuenta como un correo. La pantalla de *campañas* de Brevo no se usa: el servicio envía por la API transaccional.

### 8.2. SMTP (respaldo)

Se selecciona con `MAIL_PROVIDER=smtp`. Funciona con Gmail (contraseña de aplicación, que requiere verificación en dos pasos), Mailpit u otro servidor.

- Puerto `465`: TLS implícito. Otros puertos: STARTTLS si `SMTP_STARTTLS=true`.
- Se envía un mensaje con parte de texto plano (generada quitando las etiquetas) y parte HTML.
- Errores de autenticación, destinatario rechazado o remitente rechazado: permanentes. Respuestas `4xx`, red y tiempos de espera agotados: temporales. Respuestas `5xx`: permanentes.
- Gmail reescribe el remitente a la cuenta autenticada.

### 8.3. Modos

| Modo | Cuándo se activa | Comportamiento |
| --- | --- | --- |
| `dry-run` | `EMAIL_DRY_RUN=true`, o el proveedor no tiene credenciales | No envía nada; escribe cada correo en el log. |
| `sandbox` | `SANDBOX_TO` definido | Envía de verdad, pero **todos** los correos van a esa dirección, con el asunto prefijado `[TEST -> destinatario original]`. |
| `live` | Credenciales y sin `SANDBOX_TO` | Envía a los destinatarios reales. |

`dry-run` tiene prioridad sobre los demás. Para pruebas con usuarios de la base de datos conviene mantener `SANDBOX_TO` activo.

## 9. Configuración

Variables del servicio, leídas de `notifications_service/.env` (plantilla: `.env.example`). Para aplicar cambios hay que **recrear** el contenedor; `docker compose restart` no relee el archivo.

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `RABBITMQ_URL` | `amqp://undesparche:undesparche@rabbitmq:5672/` | Conexión al broker. En el compose se fija con `environment`, que tiene prioridad sobre el `.env`. |
| `PREFETCH` | `10` | Mensajes sin confirmar por consumidor. |
| `MAX_RETRIES` | `5` | Reintentos antes de enviar a la cola de mensajes muertos. |
| `RETRY_DELAY_MS` | `30000` | Espera entre reintentos. |
| `MAIL_PROVIDER` | `brevo` | `brevo` o `smtp`. |
| `BREVO_API_KEY` | vacío | Clave de la API de Brevo (`xkeysib-...`). |
| `MAIL_FROM_EMAIL` | `no-reply@example.com` | Remitente; con Brevo debe estar verificado. |
| `MAIL_FROM_NAME` | `UNdesparche` | Nombre visible del remitente. |
| `MAIL_REPLY_TO` | vacío | Dirección de respuesta opcional. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_STARTTLS` | vacío, `587`, vacío, vacío, `true` | Respaldo SMTP. |
| `SANDBOX_TO` | vacío | Redirige todos los correos a esta dirección. |
| `EMAIL_DRY_RUN` | `false` | Fuerza el modo `dry-run`. |
| `FRONTEND_URL` | `http://localhost:5172` | Disponible para las plantillas; las actuales no lo usan. |
| `DEV_ENDPOINTS` | `false` | Habilita `POST /dev/publish`. |

Variables opcionales de los **productores** (en Events e Inventory): `RABBITMQ_URL` (con `pika` el vhost por defecto se escribe `%2F` al final), `NOTIFICATIONS_ENABLED` (`true` por defecto; `false` apaga la publicación), y solo en Events `FRONTEND_URL` y `UNSUBSCRIBE_PATH` (`/unsubscribe` por defecto), que arman el enlace de baja.

## 10. Integración de los productores

Se entregó una integración de referencia que mantiene el cambio mínimo sobre Events e Inventory. Puede usarse tal cual o como guía; conviene usar **una sola versión** para no duplicar correos.

**Publicador** (`core/notifications_publisher.py`, uno por servicio, con `pika`): publica en un hilo aparte, usa confirmaciones del broker y `mandatory`, declara el exchange (no la cola) y **nunca lanza excepciones**: si RabbitMQ está caído o la cola no existe, registra el error y la API sigue respondiendo.

**Puntos de enganche:**

| Servicio | Acción | Función | Notificación |
| --- | --- | --- | --- |
| Events | `subscribe` | `notify_subscribed` | `event.subscribed` para el suscriptor. |
| Events | `perform_update` (PATCH) | `notify_event_update` | A todos los suscriptores con correo. Un cambio de estado a `CAN`, `ECU` o `FIN` tiene prioridad; si no, un cambio de fecha o de lugar genera `event.rescheduled`. Solo para eventos publicados. |
| Inventory | crear reserva | `notify_reserved` | `implement.reserved`, con `transaction.on_commit`. |
| Inventory | confirmar reserva | `notify_borrowed` | `implement.borrowed`, con `on_commit`. |
| Inventory | registrar devolución | `notify_returned` | `implement.returned`, con `on_commit`. |
| Inventory | `release_expired_reserves` | `notify_reserve_expired` | `implement.reserve_expired`, con `on_commit`. |

Los destinatarios salen de la base de datos: en Events, los `notification_email` de las suscripciones, cada uno con un `unsubscribe_url` construido con el token firmado de `make_unsubscribe_token`; en Inventory, el correo del usuario de la reserva o préstamo.

## 11. Despliegue

`docker-compose.yml` agrega dos servicios y un volumen; los demás servicios no cambian.

| Servicio | Puertos | Detalle |
| --- | --- | --- |
| `rabbitmq` | `5672` (AMQP), `15672` (consola web) | Imagen `rabbitmq:3-management`, usuario y clave `undesparche` (solo desarrollo), volumen `rabbitmq_data` y verificación de salud. |
| `notifications_service` | `8090` | Espera a que RabbitMQ esté saludable. `env_file` opcional (`notifications_service/.env`). |

```bash
cp notifications_service/.env.example notifications_service/.env
docker compose up --build rabbitmq notifications_service
```

La imagen usa `python:3.13-slim` y arranca con `uvicorn app.main:app --port 8090`. El puerto `5672` es AMQP y **no se abre en el navegador**; la consola está en `http://localhost:15672`.

## 12. Pruebas y estado de verificación

**Prueba manual (sin productores):**

1. Abrir `http://localhost:8090/health` y comprobar `rabbitmq: true` y el modo esperado.
2. En `http://localhost:8090/docs`, ejecutar `POST /dev/publish` con un mensaje válido y un `message_id` nuevo.
3. Revisar en el log `Procesado ... enviados=N fallas_temporales=0 fallas_permanentes=0` y la bandeja de entrada (también spam). En Brevo, la sección *Transactional → Logs* muestra el estado de cada envío.
4. Para destinatarios reales: dejar `SANDBOX_TO` vacío, recrear el servicio y publicar con correos de personas que estén avisadas.

**Pruebas automáticas:** `tests/test_templates.py` (con `pytest`) renderiza los 9 tipos, comprueba la hora de Bogotá, la validación de campos y el escape de HTML.

**Estado de verificación:**

- Probado de punta a punta por el equipo: `POST /dev/publish`, consumidor y envío real con Brevo, con entrega en tres buzones (cuenta institucional, cuenta de un compañero y Gmail).
- Verificado con simulaciones: clasificación de errores del proveedor, modo `sandbox`, modo `dry-run` y renderizado de las 9 plantillas.
- **No verificado todavía:** integración de los productores con peticiones reales, reintentos y cola de mensajes muertos bajo fallas reales, y el envío por SMTP/Gmail.

## 13. Seguridad

1. **Credenciales:** `notifications_service/.env` contiene la clave de Brevo y no debe subirse a git. Solo se versiona `.env.example`. Si una clave se expone, hay que revocarla en Brevo y generar otra.
2. **Destinatarios decididos por el backend:** ni el navegador ni el cliente HTTP pueden elegir destinatarios o contenido.
3. **`DEV_ENDPOINTS=false`** en cualquier entorno que no sea local: `POST /dev/publish` no tiene autenticación y permite enviar correos a cualquier dirección.
4. **Credenciales del broker:** `undesparche/undesparche` es solo para desarrollo; deben cambiarse en un despliegue real.
5. **Correos reales en pruebas:** con `SANDBOX_TO` vacío los correos salen a personas reales; los usuarios sembrados en las bases de datos tienen direcciones institucionales.
6. **Plantillas:** se escapan como HTML y fallan si falta un dato (`StrictUndefined`), por lo que no se envían correos incompletos.

## 14. Limitaciones y observaciones de implementación

1. **Posibles duplicados.** Ni Brevo ni SMTP ofrecen clave de idempotencia. Si el proceso se detiene entre enviar y confirmar el mensaje, un correo podría repetirse. Para reducirlo, los reintentos incluyen solo a los destinatarios fallidos. Es un riesgo aceptado para esta entrega.
2. **Remitente institucional.** Enviar con `@unal.edu.co` a través de Brevo no está autorizado en el DNS de la universidad (SPF/DKIM), por lo que algunos proveedores pueden filtrar el correo como spam. La alternativa es verificar en Brevo una dirección personal como remitente. Tampoco hay garantía de bandeja principal con una dirección de Gmail.
3. **Límite diario.** El plan gratuito de Brevo permite 300 correos por día.
4. **Reservas vencidas.** `release_expired_reserves` se ejecuta cuando alguien consulta implementos o reservas, no por reloj. El correo de reserva vencida puede llegar con retraso si nadie consulta.
5. **Eventos que inician o terminan.** El estado `ECU` o `FIN` solo cambia por PATCH; no hay un proceso programado que lo haga al llegar la hora, por lo que `event.started` y `event.finished` solo se envían cuando alguien cambia el estado.
6. **Préstamos sin fecha límite.** El modelo `Borrowing` no tiene fecha de vencimiento, por lo que no existe una notificación de préstamo vencido.
7. **Enlace de baja.** Apunta a `FRONTEND_URL + UNSUBSCRIBE_PATH + ?token=...`. El frontend debe tener esa ruta y llamar al endpoint de baja por token de Events. El token se genera con la firma de Django y no se comprueba su antigüedad, por lo que el enlace no caduca.
8. **Tipos sin validar en la entrada HTTP.** `POST /dev/publish` acepta cualquier `type`; el rechazo ocurre en el consumidor.
9. **Espera de reintento fija.** No hay espera exponencial.
10. **Mensajes perdidos sin cola.** Si un productor publica antes de que Notifications haya arrancado por primera vez, la cola no existe y el mensaje no se puede enrutar; el publicador de referencia lo registra en el log.
11. **Estado de eventos publicados.** Los correos de eventos solo se envían si el evento está publicado; un evento sin publicar no tiene suscriptores.

## 15. Solución de problemas

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `GET /` en el puerto 8090 devuelve `Not Found` | No hay ruta raíz. | Usar `/health`, `/types` o `/docs`. |
| El puerto 5672 «no funciona» en el navegador | Es AMQP, no HTTP. | Usar la consola en el puerto 15672. |
| `/dev/publish` no aparece en `/docs`, o el modo es `dry-run` | El `.env` no se cargó (nombre incorrecto, como `.env.txt`, o contenedor sin recrear). | Verificar el archivo y ejecutar `docker compose up -d --force-recreate notifications_service`. Para ver lo que recibió el contenedor: `docker compose exec notifications_service printenv`. |
| `422` con `string_too_short` en `message_id` | Se usó un valor de ejemplo como `uuid-v4`. | Enviar un identificador de 8 o más caracteres. |
| `422` con `json_invalid` | JSON mal formado (por ejemplo, `recipients` dentro de `data` o falta una coma). | `recipients` va en el nivel superior del mensaje. |
| Log con `Falla permanente para ...` | Dirección rechazada o remitente no verificado en Brevo. | Revisar el motivo del log y la verificación del remitente. |
| Error `403` de Brevo | Remitente sin verificar o restricción de direcciones IP en Seguridad. | Verificar el remitente; desactivar o ampliar la lista de IP autorizadas. |
| El correo llega a spam | Remitente sin autenticación de dominio. | Marcar como «no es spam» o usar otro remitente verificado. |
| Log con `No se pudo encolar la notificación` en un productor | RabbitMQ caído o cola inexistente. | Levantar `rabbitmq` y `notifications_service`. |
| `IsADirectoryError` o `Invalid symbol 95` al abrir Events o Inventory | `firebase_credentials.json` es una carpeta o contiene valores de ejemplo. | No está relacionado con las notificaciones: colocar el JSON de credenciales real, o usar los tokens de desarrollo `demo_user_<correo>` con `DEBUG=True`. |

## 16. Estructura del proyecto

```text
notifications_service/
  Dockerfile
  requirements.txt
  .env.example
  README.md
  CONTRATO_MENSAJES.md
  app/
    main.py        FastAPI, lifespan, /health, /types, /dev/publish
    config.py      variables de entorno
    schemas.py     esquema del mensaje (Pydantic)
    catalog.py     tipos, asuntos y campos obligatorios
    rendering.py   Jinja2, formato de fechas en hora de Bogotá
    consumer.py    topología RabbitMQ, consumo, reintentos y mensajes muertos
    mailer.py      Brevo (API) y SMTP, clasificación de errores
    templates/     base.html y una plantilla por tipo
  tests/
    test_templates.py
```

Archivos de referencia para los productores: `core/notifications_publisher.py` y `<app>/notifications.py` en `events_service` e `inventory_service`.
