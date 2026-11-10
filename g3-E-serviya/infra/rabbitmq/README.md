# RabbitMQ — topología compartida del chat

## Topología

Vhost: `/`.

| Exchange | Tipo | Uso |
|---|---|---|
| `serviya.requests` | topic | Eventos de solicitudes hacia el chat |
| `serviya.users` | topic | Eventos de usuarios hacia el chat |
| `serviya.chat` | topic | Eventos del chat hacia métricas/reportes/soporte |
| `serviya.chat.dlx` | direct | Dead-letter de las colas del chat |
| `serviya.notifications` | topic | Comando `notification.requested.v1` (existente) |

Las colas `chat-service.requests` y `chat-service.users` (y sus DLQ) las declara el
`chat-service`; no este script. El script crea los exchanges, el usuario `chat` y sus permisos.

## Uso

```bash
set -a
. ./.env
set +a
./infra/rabbitmq/setup-topology.sh
```

Requiere que RabbitMQ esté arriba y el management API accesible en
`http://localhost:15672/api` (o `RABBITMQ_MANAGEMENT_URL`).

## Variable nueva

Añade a tu `.env` local:

```env
CHAT_RABBITMQ_PASSWORD=<password aleatorio>
```

El usuario `chat` solo puede:

- configurar sus propias colas y `serviya.chat.dlx`;
- publicar en `serviya.chat` y `serviya.notifications`;
- leer sus colas `chat-service.*`.

No puede publicar en `serviya.requests` ni `serviya.users`.
