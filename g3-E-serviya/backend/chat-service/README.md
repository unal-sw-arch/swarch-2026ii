# chat-service

Chat 1 a 1 entre **cliente** y **oferente** de ServiYa, asociado a una solicitud de servicio (fase 1 / MVP de la
guía). Es un microservicio independiente: BD propia, Redis Pub/Sub propio, y comunicación con el resto solo por el
API gateway (REST + WebSocket) y RabbitMQ.

- Diseño y requisitos: [`guia-microservicio-chat.md`](../../documents/microservicios/chat/guia-microservicio-chat.md)
  y [`requisitos-infraestructura-chat.md`](../../documents/microservicios/chat/requisitos-infraestructura-chat.md).
- Contrato de infraestructura compartida (red, puertos, usuarios, topología, envelope):
  [`decisiones-infraestructura-implementacion.md`](../../documents/microservicios/chat/decisiones-infraestructura-implementacion.md).
- Decisiones tomadas al implementar (incluido el cambio a Java/Spring): [`docs/adr/`](docs/adr/README.md).
- Contratos: REST [`docs/openapi.json`](docs/openapi.json) · WebSocket y eventos [`docs/asyncapi.yaml`](docs/asyncapi.yaml).
- Frontend que lo consume: [`frontend/serviya/src/modules/chat/`](../../frontend/serviya/src/modules/chat/README.md).

## Índice

1. [Qué se implementó](#qué-se-implementó)
2. [Arquitectura](#arquitectura)
3. [Modelo de dominio y datos](#modelo-de-dominio-y-datos)
4. [Cómo funciona](#cómo-funciona)
5. [Interfaces](#interfaces)
6. [Seguridad](#seguridad)
7. [Resiliencia y degradación](#resiliencia-y-degradación)
8. [Requisitos previos y despliegue](#requisitos-previos)
9. [Probarlo a mano](#probarlo-a-mano-sin-frontend-ni-monolito)
10. [Pruebas](#pruebas)
11. [Runbook breve](#runbook-breve)
12. [Pendiente](#pendiente)

## Qué se implementó

Fase 1 completa de la guía (MVP):

| Requisito | Cómo |
|---|---|
| RF-CHAT-01/02 Conversación al crearse la solicitud, una por solicitud | Consumidor de `requests.service_request.created` + índice único sobre una columna generada |
| RF-CHAT-03/31 Listado por actividad, con último mensaje, no leídos y filtros combinables | Una sola consulta SQL con filtros `IN (...)` opcionales y paginación por cursor |
| RF-CHAT-04 Solo participantes | Verificado en el caso de uso (dominio), no solo en el controlador |
| RF-CHAT-05 Solo lectura definitiva al terminar la solicitud | Consumidor de `status_changed`; la conversación nunca vuelve a `ACTIVE` |
| RF-CHAT-10/11/13 Mensajes en tiempo real y en orden | WebSocket + Redis Pub/Sub; `seq` por conversación asignado con bloqueo de fila |
| RF-CHAT-12 Historial paginado | Por `seq` (`before_seq` / `after_seq`) |
| RF-CHAT-14 Confirmación de lectura | Puntero `last_read_seq` que solo avanza + evento `conversation.read_updated` |
| RF-CHAT-15 Aviso al desconectado, sin contenido | El chat decide y emite `notification.requested.v1` (contrato existente) |
| RF-CHAT-16 Reenvíos sin duplicar | Idempotencia por `client_message_id` (índice único + lectura tras el bloqueo) |
| RF-CHAT-17 Longitud máxima | 2000 caracteres, validado en el dominio |
| RF-CHAT-30 Suspendido no escribe | Proyección local de usuarios alimentada por `users.user.*` |
| RF-CHAT-32 Advertir datos de contacto | Detector en el dominio: el mensaje se guarda y el ACK trae `warnings` |
| RNF-CHAT-06/08 Cero pérdidas | ACK solo tras el commit; eventos por outbox transaccional |
| RNF-CHAT-09 Consumo idempotente | Tabla `processed_events` + manejadores idempotentes |
| RNF-CHAT-11 JWT en todo | Resource server ES256/JWKS (`iss`, `aud`, `exp`, `typ=at+jwt`) |
| RNF-CHAT-12 Anti-spam | Rate limiting por usuario en `redis-coord`, con respaldo en memoria |
| RNF-CHAT-15/16 Resiliencia y recuperación | Proyecciones locales (sin llamadas síncronas al monolito) + `sync` tras reconectar |
| RNF-CHAT-17 Observabilidad | `X-Request-Id` de punta a punta, logs JSON opcionales, health checks |
| RNF-CHAT-21 Contratos | OpenAPI generado y protegido por prueba; AsyncAPI validado |

**Qué no hace (aún):** chat directo sin solicitud, bloqueo, archivar, «escribiendo…», presencia con TTL, borrado de
mensajes (fase 2); adjuntos (fase 3); soporte con administradores (fase 4). No emite tokens ni envía correos.

## Arquitectura

```text
              navegador ──HTTPS/WSS──▶ nginx ─▶ API gateway ── /api/chat/** ──▶ chat-service :8000
                                                                                 │  │  │  │
          ┌──────────────────────────────────────────────────────────────────────┘  │  │  │
          ▼                                ▼                                         ▼  │
   MySQL 8.4 propio             redis-coord (compartido)                  chat-pubsub (propio)
   conversaciones, mensajes,    tickets WS, rate limiting                 difusión entre instancias,
   outbox, proyecciones         usuario ACL `chat`, claves chat:*         «¿está en línea?»
                                                                                        │
                       RabbitMQ (vhost /, usuario `chat`) ◀─────────────────────────────┘
        consume: serviya.requests, serviya.users   ·   publica: serviya.chat, serviya.notifications
```

**Hexagonal (Escuela B, como el monolito):** puertos en `application`, dominio sin frameworks. `domain` y
`application` no importan Spring, JPA ni Jackson. Los casos de uso son clases planas que se instancian en
`shared/infrastructure/config/UseCaseConfig`. Las transacciones pasan por el puerto `UnitOfWork`, para que la
difusión en tiempo real ocurra estrictamente después del commit.

```text
src/main/java/com/parosurvivors/chat/
├── conversations/
│   ├── domain/          Conversation (agregado), Participant, ConversationStatus/Type, TerminalRequestStatuses
│   ├── application/     OpenRequestConversation, ApplyRequestStatus, ListMyConversations, GetConversation, MarkAsRead
│   └── infrastructure/  REST (ConversationController), consumidor RequestEventsListener, JPA y modelo de lectura SQL
├── messaging/
│   ├── domain/          Message (inmutable), ContactInfoDetector
│   ├── application/     SendMessage, GetConversationHistory, SyncMessages
│   └── infrastructure/  REST (MessageController), JPA
├── directory/           proyección local de usuarios + UserEventsListener
├── realtime/            WebSocket (handler, registro de conexiones), tickets, Pub/Sub, presencia, rate limiting
└── shared/
    ├── application/     puertos transversales (UnitOfWork, EventPublisher, RealtimeNotifier, OnlineUsers, RateLimiter, NotificationRequester)
    └── infrastructure/  outbox + relay, topología RabbitMQ, seguridad, web (errores, correlación), config (Redis, casos de uso, OpenAPI)
```

Stack: Java 21, Spring Boot 3.5 (MVC, WebSocket, Data JPA/JDBC, AMQP, Data Redis/Lettuce, OAuth2 Resource
Server), Flyway, MySQL 8.4 ([ADR-0002](docs/adr/0002-java-spring-en-vez-de-python.md)).

## Modelo de dominio y datos

**`Conversation`** (agregado raíz):
- Atributos: `type` (`REQUEST`; `DIRECT`/`SUPPORT` reservados para fases 2 y 4), `context_id` (id de la solicitud),
  `status` (`ACTIVE` / `READ_ONLY`), `context_status` (estado de la solicitud copiado tal cual, para los filtros),
  `last_seq`, `last_message_at` y dos `Participant` (`CLIENT`, `OFFERER`) con su `last_read_seq`.
- Invariantes:
  - Solo los participantes leen o escriben.
  - Solo se escribe en `ACTIVE`.
  - `READ_ONLY` es definitivo.
  - Los eventos de estado más viejos que el último aplicado se ignoran.
  - El puntero de lectura nunca retrocede.
  - Un tercero recibe «no participas», sin revelarle si la conversación está en solo lectura.
- Estados terminales (configurables): `REJECTED`, `CANCELLED`, `COMPLETED`, `RESCHEDULED`, `NOT_PROVIDED`.

**`Message`** (inmutable):
- Atributos: `seq` (orden total y sin huecos dentro de la conversación), `kind` (`TEXT` / `SYSTEM`), `body` (sin
  espacios en los extremos, 1–2000 caracteres) y `flags` (`CONTACT_INFO`).
- `ContactInfoDetector` marca celulares colombianos, números de 10+ dígitos, correos y enlaces. Deja pasar
  precios («$1.500.000»), direcciones y horas.

**Tablas** (Flyway `V1__init_chat_schema.sql`):

| Tabla | Propósito | Restricciones clave |
|---|---|---|
| `conversations` | Agregado | `request_ctx` = columna generada `IF(type='REQUEST', context_id, NULL)` + `UNIQUE` → una conversación por solicitud (emula el índice parcial que MySQL no tiene) |
| `conversation_participants` | Participantes y lectura | PK `(conversation_id, user_id)` |
| `messages` | Mensajes | `UNIQUE(conversation_id, seq)` orden; `UNIQUE(conversation_id, sender_id, client_message_id)` idempotencia |
| `outbox_events` | Eventos pendientes de publicar | `event_id` único; índice `(published_at, id)` |
| `processed_events` | Eventos consumidos | PK `event_id` |
| `user_directory` | Proyección de usuarios (nombre, avatar, estado) | Sin email ni teléfono (minimización) |

Fechas en `DATETIME(6)` UTC. Todos los ids son `BIGINT`, y `client_message_id` es un token opaco del cliente.

## Cómo funciona

### Apertura de una conversación

```text
monolito ─outbox─▶ serviya.requests  requests.service_request.created
   ▶ cola chat-service.requests ▶ RequestEventsListener
       ▶ ¿event_id ya procesado? → ignora
       ▶ OpenRequestConversation: crea conversación + 2 participantes y escribe chat.conversation.created en el outbox
         (si la solicitud ya llegó terminal, nace en solo lectura)
       ▶ tras el commit: conversation.created por WebSocket a ambos participantes
       ▶ marca el event_id como procesado
```

Si llegan dos eventos a la vez para la misma solicitud, gana el índice único. El otro relee en una transacción nueva,
porque la violación deja la transacción en curso marcada para rollback, y devuelve la conversación existente.

### Envío de un mensaje

El mismo caso de uso (`SendMessageService`) atiende WebSocket (`message.send`) y REST (`POST …/messages`):

```text
1. valida client_message_id (1–36 caracteres) y el rate limit del usuario
2. TRANSACCIÓN (READ COMMITTED):
   a. SELECT … FOR UPDATE de la conversación   ← serializa los envíos de ESA conversación
   b. ¿ya existe (conversación, emisor, client_message_id)? → devuelve el guardado (duplicate=true), sin efectos
   c. autoriza: participante, no suspendido, conversación ACTIVE
   d. seq = last_seq + 1; valida el texto y detecta datos de contacto; INSERT del mensaje
   e. avanza last_seq / last_message_at y el last_read_seq del emisor (su mensaje no le cuenta como no leído)
   f. ¿destinatario en línea? (PUBSUB NUMSUB)
   g. outbox: chat.message.sent (sin contenido) y, si está desconectado, notification.requested.v1
3. COMMIT → recién aquí sale el ACK (message.ack / 201)
4. message.new por Pub/Sub al destinatario y a las otras sesiones del emisor (best effort)
```

- **Orden sin huecos:** el bloqueo de fila más `UNIQUE(conversation_id, seq)`. Probado con 30 envíos simultáneos →
  `seq` 1..30. Sin el bloqueo, las pruebas de concurrencia fallan.
- **Idempotencia:** READ COMMITTED garantiza que, tras el bloqueo, se ve lo que confirmó el envío anterior
  ([ADR-0017](docs/adr/0017-transacciones-y-outbox.md)). 12 reenvíos simultáneos dejan un solo mensaje.
- **Nunca se pierde un mensaje confirmado:** el ACK sale después del commit. Si el tiempo real falla, el mensaje ya
  está guardado y el cliente lo recupera con `sync` o REST.
- **El aviso a Notificaciones lo decide y compone el chat** ([ADR-0016](docs/adr/0016-comando-de-notificacion-v1.md)):
  - Texto: «Tienes un mensaje nuevo de {nombre}», con el nombre de `user_directory`.
  - Canal: `INTERNAL`.
  - Nunca lleva el cuerpo del mensaje.
  - Si no se puede saber quién está en línea, se notifica por si acaso.

### Cambio de estado de la solicitud

`requests.service_request.status_changed` (publicado en cada transición):
- Actualiza `context_status` para los filtros.
- Ignora eventos con `occurred_at` más viejo que el último aplicado.
- Si el estado es terminal, pasa la conversación a `READ_ONLY`, escribe `chat.conversation.read_only` en el outbox y
  avisa por WebSocket con `conversation.status_changed`.
- Si la conversación todavía no existe (el `created` no llegó), lanza una excepción: el consumidor reintenta con
  backoff y, agotados los intentos, el evento va a la DLQ.

### Lectura

`conversation.read` (WebSocket) o `POST …/read`:
- El valor se acota al último mensaje y el puntero se avanza con `GREATEST`. Una copia vieja de la conversación nunca
  pisa un «marcar leído» concurrente.
- Si avanzó, se avisa a los participantes con `conversation.read_updated`. No leídos = `last_seq − last_read_seq`.

### Tiempo real

1. **Ticket** (`POST /ws-tickets`, con JWT): 32 bytes aleatorios guardados en `redis-coord` como
   `chat:wsticket:{ticket}` con TTL de 30 s.
2. **Handshake** `GET /api/chat/v1/ws?ticket=…`: se valida el `Origin` contra `ALLOWED_WS_ORIGINS`. Luego se consume
   el ticket con `GET` + `DEL` (el ACL del usuario `chat` no tiene `GETDEL`; solo gana quien logra borrar, así sigue
   siendo de un solo uso aun con lecturas simultáneas).

   | Cierre | Motivo |
   |---|---|
   | `4401` | Ticket inválido, usado o vencido |
   | `4403` | Usuario suspendido o eliminado |
   | `1013` | `redis-coord` no disponible |

3. **Registro de conexiones** (`ConnectionRegistry`): cada instancia mantiene `user_id → sesiones`. Con la primera
   sesión de un usuario se suscribe a `chat:user:{id}` en el Pub/Sub propio, y con la última se desuscribe
   ([ADR-0015](docs/adr/0015-difusion-por-canal-de-usuario.md)).
4. **Difusión:** se publica el frame JSON ya listo en el canal de cada destinatario. La instancia que lo tiene
   conectado lo entrega. No hacen falta sesiones pegajosas.
5. **¿En línea?** `PUBSUB NUMSUB chat:user:{id}` > 0, sin claves de presencia que mantener. Se usa la API nativa de
   Lettuce, porque el `execute` genérico de Spring no lee respuestas enteras.
6. **Mantenimiento:**
   - ping de protocolo cada 25 s;
   - frames de más de 8 KB → cierre `1009`;
   - sesiones lentas → cierre (el decorador concurrente limita tiempo y buffer de envío);
   - al apagarse la instancia → `1001` a todos, para que reconecten a otra.
7. **Recuperación:** el cliente envía `sync` con `{conversation_id, last_seq}` y recibe en `sync.result` lo que se
   perdió (hasta 200 por conversación; `has_more` indica si hay que seguir por REST).

### Eventos salientes: outbox + relay

- Cada evento se escribe en `outbox_events` **en la misma transacción** que el cambio de negocio:
  - eventos de dominio con el sobre acordado: `event_id`, `event_type`, `event_version`, `occurred_at`, `producer`,
    `subject` (`conversation/{id}`), `correlation_id` y `causation_id`;
  - el comando `notification.requested.v1` con su formato v1 propio.
- `OutboxRelayScheduler` corre cada segundo:
  1. reclama un lote con `FOR UPDATE SKIP LOCKED` (varias instancias sin duplicar);
  2. lo publica con *publisher confirms*, como mensaje persistente con `message_id = event_id`;
  3. marca `published_at` **solo** tras la confirmación del broker.
- Con RabbitMQ caído las filas esperan, se cuenta el intento y el log avisa al primer fallo y luego ~1 vez por
  minuto. Entrega al menos una vez: los consumidores deduplican por `event_id`.
- **Correlación:** `correlation_id` viene de `X-Request-Id`, o del evento consumido. `causation_id` es el
  `event_id` que provocó el evento. Así se sigue una solicitud del monolito hasta la notificación.

### Eventos entrantes

- La cola `chat-service.requests` está enlazada a `serviya.requests` con `requests.service_request.*`. La cola
  `chat-service.users` está enlazada a `serviya.users` con `users.user.*`. Cada una tiene su DLQ (`….dlq`) vía
  `serviya.chat.dlx`.
- El chat declara solo sus colas, bindings y DLQ. Los exchanges los declara infraestructura
  (`infra/rabbitmq/setup-topology.sh`).

| Situación | Resultado |
|---|---|
| JSON o campos inválidos | `AmqpRejectAndDontRequeueException` → DLQ sin reintentos |
| Fallo transitorio (BD caída, `status_changed` antes que `created`) | Reintentos con backoff → DLQ |
| `event_id` ya procesado | Se confirma sin efectos |
| Tipo que el chat no usa (p. ej. `…accepted`) | Se confirma y se ignora (no va a la DLQ) |
| Campos desconocidos (`subject`, `causation_id`, `created_at`, `roles`…) | Se ignoran |

La proyección de usuarios (`UserProjectionService`):
- Ignora eventos más viejos que la fila.
- En `updated`, un campo `null` significa «no cambió».
- Anonimiza en `deleted` («Usuario eliminado»), y una cuenta eliminada no vuelve.
- Si un evento de estado llega antes que el registro, crea una fila provisional.

## Interfaces

**REST** (todas con `Authorization: Bearer`; JSON en `snake_case`; contrato completo en
[`docs/openapi.json`](docs/openapi.json)):

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/chat/v1/conversations?status=&request_status=&type=&cursor=&limit=` | Listado (filtros multivaluados separados por coma) |
| `GET` | `/api/chat/v1/conversations/{id}` | Detalle (participantes y punteros de lectura) |
| `GET` | `/api/chat/v1/conversations/by-request/{requestId}` | Conversación de una solicitud |
| `GET` | `/api/chat/v1/conversations/{id}/messages?before_seq=&after_seq=&limit=` | Historial (orden ascendente) |
| `POST` | `/api/chat/v1/conversations/{id}/messages` | Envío de respaldo: 201 nuevo, 200 reenvío reconocido |
| `POST` | `/api/chat/v1/conversations/{id}/read` | Marcar leído hasta `seq` |
| `POST` | `/api/chat/v1/ws-tickets` | Ticket para el WebSocket |

Los errores siguen RFC 9457 (`application/problem+json`) con `type`, `title`, `status`, `code` y `trace_id`:

| HTTP | Códigos |
|---|---|
| 403 | `CHAT_NOT_PARTICIPANT`, `CHAT_USER_SUSPENDED` |
| 404 | `CHAT_CONVERSATION_NOT_FOUND` |
| 409 | `CHAT_CONVERSATION_READ_ONLY` |
| 429 | `CHAT_RATE_LIMITED` (con `Retry-After`) |
| 503 | `CHAT_TICKETS_UNAVAILABLE` |
| 400 | Validaciones (mensaje vacío o largo, filtros, cursor, paginación) |

**WebSocket** `/api/chat/v1/ws`: frames JSON `{type, id?, payload}`. Detalle en
[`docs/asyncapi.yaml`](docs/asyncapi.yaml).

| Cliente → servidor | Servidor → cliente |
|---|---|
| `message.send`, `conversation.read`, `sync`, `ping` | `message.ack`, `message.new`, `conversation.read_updated`, `conversation.created`, `conversation.status_changed`, `sync.result`, `pong`, `error` |

Los errores de una operación llegan como frame `error` con el `code` y el `ref_id` del frame que los causó. La
conexión sigue abierta.

## Seguridad

- **JWT ES256 por JWKS** (`JwtDecoderConfig`):
  - ES256 fijado explícitamente (el default de Spring es RS256);
  - valida `exp`, `iss`, `aud` y `typ=at+jwt` (Nimbus solo acepta `JWT` por defecto);
  - el JWKS se cachea por `kid`;
  - el chat no tiene material de firma y no comprueba `min_iat` (lo hace el gateway);
  - sin `JWT_ISSUER` el servicio no arranca.
- **Autorización** en el caso de uso, por participante. Las conversaciones `REQUEST` solo se crean por eventos.
- **WebSocket:** ticket de un solo uso (el JWT no va en la URL) y validación de `Origin`.
- **Mínimo privilegio:**
  - Redis: usuario `chat` con solo `~chat:*` y los comandos necesarios; ninguna clave sin TTL.
  - RabbitMQ: usuario `chat`, que no puede publicar en `serviya.requests` ni `serviya.users`.

  Las pruebas usan el ACL y los permisos **reales** de `infra/` y verifican ambas restricciones.
- **Privacidad:** el contenido de los mensajes nunca va en logs, eventos ni notificaciones. `X-Request-Id` solo se
  acepta si tiene un formato razonable.

## Resiliencia y degradación

| Dependencia | Para qué | Si cae |
|---|---|---|
| MySQL propio (`chat-mysql`) | Fuente de verdad | El servicio no funciona |
| `redis-coord` | Tickets WS, rate limiting | Tickets → 503 (el cliente usa REST); rate limiting en memoria por instancia; las conexiones abiertas siguen |
| `chat-pubsub` | Difusión, «¿en línea?» | Los mensajes se guardan; el destinatario los recupera con `sync`; se notifica por si acaso |
| RabbitMQ | Eventos | Salientes esperan en el outbox; entrantes esperan en la cola |
| JWKS | Verificar tokens | Las claves ya cacheadas siguen validando |
| Monolito | — | Ninguna llamada síncrona: el chat sigue con conversaciones existentes |

Redis no tumba el servicio al arrancar: las conexiones son perezosas y con timeouts cortos.

## Requisitos previos

- Docker y Docker Compose. JDK 21 + Maven solo para desarrollar fuera de Docker.
- El ACL de `redis-coord` generado (incluye el usuario `chat`):
  ```bash
  (cd infra/redis/acl && set -a && . ../../../.env && set +a && ./render-acl.sh users.coord.acl.tpl users.coord.acl)
  ```
- La topología compartida de RabbitMQ y el usuario `chat`, con el broker arriba (necesita `curl` y `jq`):
  ```bash
  set -a; . ./.env; set +a; ./infra/rabbitmq/setup-topology.sh
  ```
  El chat declara después **sus** colas, bindings y DLQ al arrancar. Sin este paso el usuario `chat` no existe.
- Un emisor de JWT con JWKS (ver abajo; Auth lo publicará detrás del gateway).

## Levantarlo

Contrato de despliegue ([`decisiones-infraestructura-implementacion.md`](../../documents/microservicios/chat/decisiones-infraestructura-implementacion.md)):
puerto interno **8000**, red `serviya-redis-net`, REST por el gateway en **`/api/chat/v1/...`** y WebSocket en
**`/api/chat/v1/ws`** ([ADR-0014](docs/adr/0014-ruta-publica-api-chat-v1.md)).

Variables (ver [`.env.example`](../../.env.example)):

| Variable | Para qué |
|---|---|
| `CHAT_DB_USER`, `CHAT_DB_PASSWORD`, `CHAT_DB_ROOT_PASSWORD` | MySQL propio (`chat-mysql`) |
| `CHAT_RABBITMQ_PASSWORD` | Usuario RabbitMQ `chat` (el mismo que crea `setup-topology.sh`) |
| `REDIS_CHAT_PASSWORD` | Usuario ACL `chat` de `redis-coord` (el mismo que usa `render-acl.sh`) |
| `JWT_JWKS_URL` | Por defecto `http://gateway:8080/.well-known/jwks.json` |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Por defecto `https://auth.serviya.local` y `marketplace-api`; se validan siempre |
| `ALLOWED_WS_ORIGINS` | Orígenes permitidos en el handshake WebSocket |
| `CHAT_RATE_LIMIT_MAX` / `CHAT_RATE_LIMIT_WINDOW_SECONDS` | Opcional: 20 mensajes / 10 s |
| `LOG_STRUCTURED_FORMAT=ecs` | Opcional: logs JSON con `correlationId` |

```bash
# Desde la raíz del repo. Las migraciones (Flyway) corren solas al arrancar.
docker compose -f docker-compose.yml -f infra/redis/docker-compose.redis.yml up -d --build chat-service redis-coord gateway
```

Salud (red interna; el gateway no la expone): `GET /actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness`.

## Probarlo a mano (sin frontend ni monolito)

Herramientas en [`dev/`](dev/), solo stdlib de Python + `openssl`:

| Herramienta | Qué hace |
|---|---|
| `dev_auth.py` | Emisor ES256 de desarrollo: genera la clave, sirve el JWKS y emite tokens con los claims reales |
| `stub_producer.py` | Publica los eventos del monolito (solicitudes y usuarios) por la API de la consola de RabbitMQ |
| `ws_client.py` | Cliente WebSocket mínimo para enviar frames a mano |

```bash
cd backend/chat-service/dev
python3 dev_auth.py init && python3 dev_auth.py serve --port 9400 &   # JWKS de desarrollo
# El chat debe arrancar con JWT_JWKS_URL=http://host.docker.internal:9400/.well-known/jwks.json
# (el issuer por defecto de dev_auth ya es el acordado, https://auth.serviya.local)
TC=$(python3 dev_auth.py token --user 1001 --roles CLIENT)
TO=$(python3 dev_auth.py token --user 1002 --roles OFFERER)

# El monolito ya publica estos eventos (ChatEventPublisher); el stub sirve para probar el chat aislado.
# RABBITMQ_USER/RABBITMQ_PASSWORD = administrador del broker; usa la API de la consola (:15672)
python3 stub_producer.py user-registered --user 1001 --name "Ana Cliente"
python3 stub_producer.py request-created --request 77 --client 1001 --offerer 1002

API=http://localhost:8080/api/chat/v1
curl -H "Authorization: Bearer $TO" $API/conversations
TICKET=$(curl -s -X POST -H "Authorization: Bearer $TC" $API/ws-tickets | python3 -c 'import sys,json;print(json.load(sys.stdin)["ticket"])')
WS_ORIGIN=http://localhost:5173 python3 ws_client.py "ws://localhost:8080/api/chat/v1/ws?ticket=$TICKET"
# y escribir, por ejemplo:
# {"type":"message.send","id":"f-1","payload":{"conversation_id":1,"client_message_id":"c-1","body":"Hola"}}

python3 stub_producer.py status-changed --request 77 --status CANCELLED   # → solo lectura
```

`--event-id` reutiliza un id (probar idempotencia) y `--occurred-at` fija la hora (probar eventos fuera de orden).

## Pruebas

```bash
mvn test -DargLine="-Dapi.version=1.44"
```

- **⚠ `-Dapi.version=1.44`:** con Docker Engine ≥ 29, el cliente de Testcontainers que trae Spring Boot 3.5.5 habla
  la API 1.32 y Docker la rechaza. **Sin esa opción las pruebas de integración se SALTAN en silencio** (Maven dice
  `BUILD SUCCESS` con menos pruebas). Con Docker < 25 no la uses.

| Nivel | Qué cubre |
|---|---|
| Unitarias (sin Docker, milisegundos) | Invariantes del dominio, detector de contacto, casos de uso con fakes en memoria (guardan y devuelven copias: si un caso de uso olvida `save`, la prueba lo detecta) |
| `PersistenceIntegrationTest` | Índices únicos, puntero de lectura monótono, outbox transaccional y en UTC, listado y filtros, cursor con empates |
| `ConcurrencyIntegrationTest` | `seq` sin huecos con envíos simultáneos, reenvío simultáneo, apertura simultánea |
| `RedisAdaptersIntegrationTest` | TTL de tickets y contadores, ticket de un solo uso con 10 consumidores a la vez, respaldo en memoria, restricciones del ACL |
| `ChatEndToEndIntegrationTest` (~2,5 min) | Criterios de aceptación del MVP, seguridad JWT, DLQ, outbox con RabbitMQ pausado, permisos del usuario `chat`, contrato con los eventos reales del monolito |
| `OpenApiContractIntegrationTest` | El API no cambia sin regenerar `docs/openapi.json` |

Las de integración usan contenedores reales:
- MySQL 8.4;
- dos Redis, el de coordinación con el **ACL real** de `infra/redis`;
- RabbitMQ con los **permisos reales** de `infra/rabbitmq/setup-topology.sh`;
- un JWKS ES256 de prueba.

Si cambias la API a propósito, regenera el contrato:
`mvn test -Dtest=OpenApiContractIntegrationTest -DargLine="-Dapi.version=1.44 -Dopenapi.write=true"`.

## Runbook breve

- **Mensajes en una DLQ** (`chat-service.requests.dlq`, `chat-service.users.dlq`): inspeccionar en la consola de
  RabbitMQ.
  - Los inválidos (JSON o campos) llegan sin reintentos.
  - Un `status_changed` de una solicitud sin conversación llega tras los reintentos (el `created` no llegó o llegó
    tarde).

  Corregido el origen, mover los mensajes a la cola principal (shovel o «Move messages» de la consola).
- **Outbox creciendo** (`SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL`): revisar conexión y
  credenciales de RabbitMQ. El log avisa al primer fallo y luego ~1 vez por minuto. Se publica solo al volver.
- **Tickets en 503 / «Rate limiting en Redis no disponible»** en el log: `redis-coord` caído, lleno o con el ACL mal
  generado. Las conexiones abiertas siguen funcionando.
- **`ACCESS_REFUSED` de RabbitMQ al arrancar:** el usuario `chat` no tiene los permisos de `setup-topology.sh` (o el
  script no se ejecutó).
- **Escalar:** varias instancias funcionan sin sesiones pegajosas (Pub/Sub + `SKIP LOCKED` en el relay). Al
  reiniciar, los clientes reciben 1001 y reconectan + `sync`.

## Pendiente

- **Auth (otro desarrollador):** emisor ES256/JWKS real publicado en el gateway con `iss = https://auth.serviya.local`.
  Hasta entonces el chat solo acepta tokens de `dev/dev_auth.py` (el frontend muestra «chat no disponible»).
- **Carga inicial de `user_directory`:** el monolito publica los eventos de usuarios desde ahora, pero los usuarios que
  ya existían no tienen proyección (el chat muestra «Usuario {id}» y los deja escribir). Falta el backfill.
- **Limpieza de `outbox_events` publicados:** la tabla solo crece.
- **Varios:**
  - métricas (`/metrics`);
  - prueba de carga (RNF-CHAT-01/03);
  - reglas de arquitectura automatizadas (ArchUnit);
  - política de retención (DP-07);
  - presupuesto de memoria en `redis-coord` (DP-10).
