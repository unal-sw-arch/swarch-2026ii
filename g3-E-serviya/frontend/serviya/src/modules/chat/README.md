# Módulo `chat` (frontend)

Interfaz del chat 1 a 1 entre **cliente** y **oferente**, asociado a una solicitud de servicio. Habla con el
`chat-service` a través del API gateway: REST en `/api/chat/v1/**` y WebSocket en `/api/chat/v1/ws`.

- Backend y contratos: [`backend/chat-service/README.md`](../../../../../backend/chat-service/README.md),
  [`openapi.json`](../../../../../backend/chat-service/docs/openapi.json) (REST) y
  [`asyncapi.yaml`](../../../../../backend/chat-service/docs/asyncapi.yaml) (frames WebSocket y eventos).
- Las conversaciones **no se crean desde el frontend**: el chat-service abre una por cada solicitud cuando el
  monolito publica `requests.service_request.created`.

## Qué ve el usuario

| Dónde | Qué |
|---|---|
| Menú lateral (cliente y oferente) | Entrada **Mensajes** con contador de mensajes sin leer |
| `/messages` | Listado de conversaciones + panel de conversación (en móvil, una cosa a la vez) |
| `/messages/:conversationId` | La misma página con una conversación abierta (enlace directo) |
| Detalle de solicitud (`/requests/:id`) | Botón **Enviar mensaje al oferente/cliente**: abre la conversación de esa solicitud |

**Listado:** pestañas por estado de la conversación (Todas / Activas / Solo lectura) combinables con chips de estado
de la solicitud (Pendiente, Aceptada, Por confirmar, Completada, Cancelada, Rechazada). Cada fila muestra la
contraparte, la vista previa del último mensaje («Tú: …» si es propio), la hora, los no leídos y el estado de la
solicitud. Pagina con «Cargar más».

**Conversación:** encabezado con la contraparte, el número y estado de la solicitud y el botón «Ver solicitud»;
mensajes agrupados por día; «Cargar mensajes anteriores»; caja de texto (Enter envía, Shift+Enter salta de línea,
contador al acercarse a 2000 caracteres).

| Estado del mensaje propio | Significado |
|---|---|
| Enviando… | Aún sin confirmación del servidor |
| Enviado | Guardado (llegó el ACK) |
| Leído | La contraparte leyó hasta ese mensaje |
| No enviado · Reintentar / Descartar | Falló; el reintento reutiliza el mismo id y nunca duplica |

Además:
- **Solo lectura:** cuando la solicitud termina (cancelada, rechazada, completada…), la caja de texto se reemplaza
  por un aviso y el historial sigue visible.
- **Aviso de datos de contacto:** si el servidor detecta teléfono, correo o enlace, el mensaje se envía igual y
  aparece un aviso (descartable) sobre los riesgos de negociar fuera de ServiYa.
- **Indicador de conexión** junto al título: En línea / Conectando… / Reconectando… / Sin tiempo real.
- **Chat no disponible:** si el chat rechaza el token (401) se muestra una tarjeta informativa en lugar del chat.

## Estructura

```text
modules/chat/
├── index.js                          exporta MessagesPage (lo usa app/routes.jsx)
├── utils.js                          normalización y unión de mensajes, ids de cliente, fechas, textos de error
├── realtime/
│   ├── chatSocket.js                 cliente WebSocket del protocolo (JS puro, sin React)
│   └── index.js                      instancia única por pestaña y su ciclo de vida
├── hooks/
│   └── useChatSocket.js              useChatConnection() y useChatEvent()
├── pages/MessagesPage/
│   ├── MessagesPage.jsx              estado del listado, filtros y actualizaciones en vivo
│   └── MessagesPage.css              estilos propios (el resto viene de styles/globals.css)
└── components/
    ├── ConversationList/             listado, filtros y paginación (presentacional)
    └── ConversationView/             historial, envío, lectura, avisos (estado propio)
```

Fuera del módulo:

| Archivo | Cambio |
|---|---|
| `shared/api.js` | `chatApi`, `chatWsUrl()` y mejoras en `request()` (ver [Integración con la app](#integración-con-la-app)) |
| `shared/navConfig.js` | Entrada «Mensajes» en `CLIENT_NAV` y `OFFERER_NAV` |
| `shared/components/DashboardLayout` | Contador de no leídos en la entrada «Mensajes» |
| `shared/components/Icon` | Icono `chat` |
| `app/routes.jsx` | Rutas `messages` y `messages/:conversationId` (roles CLIENT u OFFERER) |
| `modules/requests/.../RequestDetailPage.jsx` | Botón para abrir el chat de la solicitud |
| `nginx.conf` | Ubicación con upgrade para `/api/chat/v1/ws` |

## Cómo funciona

### Flujo general

```text
MessagesPage ── REST: listConversations ──────────────▶ gateway ─▶ chat-service
     │  useChatConnection() abre el WebSocket (uno por pestaña)
     │  useChatEvent(...) actualiza el listado en vivo
     └── ConversationView (key = conversationId)
            ├── REST: getConversation + getMessages (carga inicial e historial)
            ├── WebSocket: message.send / conversation.read  (REST de respaldo)
            └── useChatEvent: message.new, read_updated, status_changed, sync
```

La fuente de verdad es siempre el servidor. El tiempo real solo acelera lo que REST también puede traer, así que
perder un evento nunca deja datos incorrectos: se recuperan con `sync` o recargando.

### Cliente WebSocket (`realtime/chatSocket.js`)

JavaScript puro que recibe sus dependencias (`issueTicket`, `wsUrl`, `WebSocketImpl`). Por eso se puede probar con
Node contra el servicio real.

1. **Ticket:** antes de cada conexión pide `POST /ws-tickets` (con el JWT) y abre
   `/api/chat/v1/ws?ticket=…`. El JWT nunca viaja en la URL. El ticket es de un solo uso y dura ~30 s.
2. **Sesión sana:** el servidor acepta el handshake *antes* de validar el ticket (uno inválido llega como cierre
   4401 justo después). Por eso, al abrir, el cliente manda un `ping`. Solo cuando llega el `pong` reinicia el
   backoff y el contador de fallos. Sin esto, un ticket rechazado una y otra vez reconectaría sin pausa.
3. **Peticiones con respuesta:** `request(type, payload)` asigna un `id` de frame y resuelve con la respuesta que
   trae ese mismo `id` (`message.ack`, `sync.result`, `pong`), o rechaza con `ChatError(code)` si llega `error`.
   Vence a los 10 s.
4. **Latido:** cada 25 s hace un `ping`. Si no hay `pong` en 10 s, cierra y reconecta. Esto detecta conexiones
   muertas que el navegador todavía cree abiertas.
5. **Reconexión:** backoff exponencial con jitter (1 s → 30 s). También reintenta enseguida cuando vuelve la red
   (`online`) o la pestaña (`visibilitychange`).
6. **Sync:** en cada (re)conexión envía `sync` con `{conversation_id, last_seq}` de lo que la UI tiene abierto. Lo
   faltante llega como evento `sync`.

| Estado | Cuándo |
|---|---|
| `connecting` / `reconnecting` | Pidiendo ticket o abriendo la conexión |
| `open` | Conectado |
| `unavailable` | El chat rechaza el token (401/403 al pedir ticket) o el ticket falla 5 veces seguidas. No insiste. |
| `suspended` | Cierre 4403: usuario suspendido o eliminado. Final. |
| `stopped` | Nadie usa el socket (se salió de la página) |

**Instancia única** (`realtime/index.js`): `getChatSocket()` crea la instancia una sola vez y la conserva, así los
componentes pueden suscribirse a eventos en cualquier orden. `acquireChatSocket()` y `releaseChatSocket()` cuentan
los usos: conecta con el primero y cierra con el último.

**Hooks** (`hooks/useChatSocket.js`):
- `useChatConnection()`: mantiene la conexión mientras el componente esté montado y devuelve el estado
  (`useSyncExternalStore`).
- `useChatEvent(type, handler)`: escucha un tipo de frame ('message.new', 'sync', 'open'…), siempre con el
  handler más reciente.

### Envío de mensajes (`ConversationView`)

1. Se genera un `client_message_id` (UUID) y el mensaje aparece al instante como **Enviando…**.
2. Si el WebSocket está abierto se envía `message.send` y se espera el `message.ack`.
3. Si no hay conexión, o se cae a mitad (`CHAT_SOCKET_CLOSED` / `CHAT_TIMEOUT`), se reintenta por
   `POST /conversations/{id}/messages` **con el mismo `client_message_id`**. El servidor reconoce el reenvío y
   devuelve el mensaje ya guardado: nunca hay duplicados.
4. Con la confirmación, el mensaje pasa a **Enviado** con su `seq` real. Si trae `warnings: ["CONTACT_INFO"]`, se
   muestra el aviso.
5. Si falla, queda **No enviado** con el motivo (`chatErrorMessage`). Los fallos de red se reintentan solos al
   reconectar. Si el error es `CHAT_CONVERSATION_READ_ONLY`, la vista pasa a solo lectura.

Los mensajes se guardan por `id` y se ordenan por `seq`, el orden total que asigna el servidor e igual para ambos
participantes (`mergeMessages`). El emisor también recibe su propio `message.new` (por sus otras pestañas), y se
deduplica por `id`.

### Lectura y no leídos

- La vista abierta marca leído hasta el último `seq` cuando cargan mensajes nuevos y la pestaña está visible
  (`conversation.read` por WebSocket o `POST /read` de respaldo). Al volver a la pestaña lo reintenta.
- «Leído» en los mensajes propios sale del `last_read_seq` de la contraparte (detalle de la conversación) y se
  actualiza con `conversation.read_updated`.
- El listado pone a 0 los no leídos de la conversación abierta, y también cuando el propio usuario lee desde otra
  pestaña (`read_updated` con su `user_id`).
- El contador del menú lo calcula `DashboardLayout` cada 30 s sumando `unread_count` de las primeras 50
  conversaciones. Si el chat responde 401, deja de consultar.

### Actualizaciones en vivo del listado (`MessagesPage`)

| Evento | Efecto |
|---|---|
| `message.new` | Actualiza vista previa y hora, suma no leídos si no es propio y sube la conversación al principio. Si la conversación no está cargada, recarga el listado. |
| `conversation.created` | Recarga el listado (llega al crearse una solicitud) |
| `conversation.status_changed` | Actualiza estado y estado de la solicitud; la vista abierta pasa a solo lectura si corresponde |
| `conversation.read_updated` (propio) | No leídos a 0 |
| `open` tras una reconexión | Recarga el listado por si se perdió algo |

### Paginación y scroll

- Listado: cursor opaco (`next_cursor`), 20 por página.
- Historial: 50 por página. Hacia atrás con `before_seq = seq` del más antiguo; al anteponer se conserva la
  posición de lectura. La vista se pega al final solo si el usuario ya estaba abajo.
- `ConversationView` se monta con `key={conversationId}`: cambiar de conversación arranca de cero, sin estado
  arrastrado.

### Seguridad

- El texto de los mensajes se renderiza **siempre como texto** (React lo escapa; CSS `white-space: pre-wrap`).
  Nunca se usa HTML.
- El JWT no viaja en la URL del WebSocket: solo el ticket de un solo uso.
- El servidor valida el `Origin` del handshake: el origen del frontend debe estar en `ALLOWED_WS_ORIGINS` del chat.

## Integración con la app

**`shared/api.js`:**
- `chatApi`: `listConversations({ status, request_status, cursor, limit })` (los arrays se envían separados por
  coma), `getConversation`, `getConversationByRequest`, `getMessages({ before_seq | after_seq, limit })`,
  `sendMessage`, `markRead` e `issueWsTicket`.
- `chatWsUrl(ticket)`: deriva `ws(s)://` de `VITE_API_URL`. Si está vacío (build detrás de nginx), usa el host de la
  página.
- `request()` ahora:
  - entiende errores `application/problem+json`, el formato del chat;
  - pone `status` y `code` en el `Error`;
  - acepta `redirectOn401: false`.

  Las llamadas del chat lo usan: **un 401 del chat no cierra la sesión de la app.**

**Por qué `redirectOn401: false`:** el chat solo acepta tokens ES256 verificados por JWKS. Hasta que el emisor
nuevo esté desplegado (trabajo de auth en curso), rechaza el token HS256 actual con 401. Con el comportamiento por
defecto, entrar a «Mensajes» habría cerrado la sesión. Ahora se muestra «El chat no está disponible» y el resto de
la app sigue igual. Cuando cambie el emisor, el chat funcionará sin tocar el frontend.

**nginx** (build de producción): `location /api/chat/v1/ws` reenvía `Upgrade`/`Connection`/`Origin` con
`proxy_read_timeout 120s`, mayor que el latido de 25 s. Sin esto, el WebSocket no pasa por nginx.

**Errores** (`utils.js → chatErrorMessage`): `CHAT_RATE_LIMITED`, `CHAT_CONVERSATION_READ_ONLY`,
`CHAT_USER_SUSPENDED`, `CHAT_NOT_PARTICIPANT` y `CHAT_MESSAGE_TOO_LONG` tienen texto propio. El resto usa el mensaje
del servidor, que ya viene en español. Un 401 se muestra como «chat no disponible».

## Probarlo en local

El chat necesita un token ES256 que el monolito todavía no emite, así que se usan las herramientas de
[`backend/chat-service/dev/`](../../../../../backend/chat-service/dev/):

```bash
# 1. Infraestructura y chat (ver backend/chat-service/README.md), con el emisor de desarrollo:
cd backend/chat-service/dev
python3 dev_auth.py init && python3 dev_auth.py serve --port 9400 &
#    el chat debe arrancar con JWT_JWKS_URL=http://host.docker.internal:9400/.well-known/jwks.json

# 2. Datos: lo que publicaría el monolito
python3 stub_producer.py user-registered --user 1001 --name "Ana Cliente"
python3 stub_producer.py user-registered --user 1002 --name "Beto Oferente"
python3 stub_producer.py request-created --request 77 --client 1001 --offerer 1002

# 3. Frontend contra el gateway
cd frontend/serviya && VITE_API_URL=http://localhost:8080 npm run dev

# 4. Token de prueba: en la consola del navegador (http://localhost:5173)
#    localStorage.setItem('serviya_token', '<salida de: python3 dev_auth.py token --user 1001 --roles CLIENT>')
#    y abrir /messages. Con otro navegador o ventana privada, el token del 1002 (OFFERER).
```

Para probar solo lectura: `python3 stub_producer.py status-changed --request 77 --status CANCELLED`.

**Verificado al implementarlo:**
- El cliente WebSocket se probó con Node contra el chat real (12 escenarios: ticket, ACK, entrega, reenvío
  idempotente, aviso de contacto, lectura, latido, reconexión + sync, error de tercero, ticket rechazado y 401).
- La UI se probó con Chromium headless, con un cliente y un oferente a la vez, en escritorio y móvil.
- El token «legado» conserva la sesión.

El módulo no tiene pruebas automáticas propias porque el proyecto aún no tiene framework de pruebas de frontend.
`chatSocket.js` es el primer candidato: no depende de React ni del DOM.

## Limitaciones conocidas

- El nombre de la contraparte sale del listado. Si se abre una conversación por enlace directo y no está en la
  página cargada del listado (p. ej. por un filtro), el encabezado muestra «Cliente» u «Oferente».
- El contador del menú se refresca cada 30 s y cuenta solo las primeras 50 conversaciones.
- La caja de texto crece con el contenido donde el navegador soporta `field-sizing` (Chrome/Edge). En Firefox es
  de una línea con scroll.
- Fuera del alcance de esta fase: chat directo sin solicitud, adjuntos, «escribiendo…», presencia, borrado de
  mensajes y soporte con administradores.
