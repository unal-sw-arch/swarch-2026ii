#!/usr/bin/env sh
# infra/rabbitmq/setup-topology.sh
#
# Declara la topologia compartida del chat en el vhost "/" y crea el usuario ACL `chat`.
# Es idempotente: se puede ejecutar varias veces.
#
# Requisitos: curl y jq. El broker debe estar arriba y el plugin de management accesible.
# Uso:
#   set -a; . ./.env; set +a
#   ./infra/rabbitmq/setup-topology.sh
#
# Variables:
#   RABBITMQ_MANAGEMENT_URL  (default http://localhost:15672/api)
#   RABBITMQ_USER            usuario administrador del broker
#   RABBITMQ_PASSWORD        password del usuario administrador
#   CHAT_RABBITMQ_PASSWORD   password del usuario `chat`
set -eu

API="${RABBITMQ_MANAGEMENT_URL:-http://localhost:15672/api}"
: "${RABBITMQ_USER:?falta RABBITMQ_USER}"
: "${RABBITMQ_PASSWORD:?falta RABBITMQ_PASSWORD}"
: "${CHAT_RABBITMQ_PASSWORD:?falta CHAT_RABBITMQ_PASSWORD}"
AUTH="${RABBITMQ_USER}:${RABBITMQ_PASSWORD}"

command -v curl >/dev/null || { echo "curl es requerido" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq es requerido" >&2; exit 1; }

put() {
  curl -fsS -u "$AUTH" -H 'Content-Type: application/json' -X PUT "$API/$1" --data-binary "$2" >/dev/null
}

echo "Declarando exchanges..."
put 'exchanges/%2F/serviya.requests' '{"type":"topic","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'
put 'exchanges/%2F/serviya.users'    '{"type":"topic","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'
put 'exchanges/%2F/serviya.chat'     '{"type":"topic","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'
# DLX directo para las colas que declare chat-service.
put 'exchanges/%2F/serviya.chat.dlx' '{"type":"direct","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'

echo "Creando/actualizando usuario chat..."
put 'users/chat' "$(jq -cn --arg password "$CHAT_RABBITMQ_PASSWORD" '{password:$password,tags:""}')"

echo "Aplicando permisos (vhost /)..."
# Lo que exige RabbitMQ para que chat-service declare SUS colas, bindings y DLQ:
#   - declarar una cola con x-dead-letter-exchange  → write en ese DLX (serviya.chat.dlx)
#   - queue.bind(cola, exchange)                     → write en la COLA y read en el EXCHANGE de origen
# Por eso write incluye chat-service.* y serviya.chat.dlx, y read incluye los exchanges de origen.
# read sobre serviya.requests/users solo permite enlazar colas a ellos; publicar exige write, que NO se concede:
# el chat sigue sin poder publicar en serviya.requests ni serviya.users.
# (Lo verifica chat-service: IntegrationTestBase lee estas tres expresiones de este archivo.)
CONFIGURE='^(chat-service\..*|serviya\.chat|serviya\.chat\.dlx)$'
WRITE='^(chat-service\..*|serviya\.chat|serviya\.chat\.dlx|serviya\.notifications)$'
READ='^(chat-service\..*|serviya\.requests|serviya\.users|serviya\.chat\.dlx)$'
put 'permissions/%2F/chat' "$(jq -cn \
  --arg configure "$CONFIGURE" \
  --arg write "$WRITE" \
  --arg read "$READ" \
  '{configure:$configure,write:$write,read:$read}')"

echo "Topologia del chat lista (vhost /)."
echo "  exchanges: serviya.requests, serviya.users, serviya.chat, serviya.chat.dlx"
echo "  usuario:   chat"
echo "  colas:     las declara chat-service (chat-service.requests/users + DLQ)"
