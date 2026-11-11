#!/usr/bin/env sh
# infra/redis/acl/render-acl.sh
# Genera un archivo ACL a partir de la plantilla y las contraseñas del entorno (.env / gestor de secretos).
# Uso:  ./render-acl.sh users.coord.acl.tpl users.coord.acl
set -eu

hash() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }

: "${REDIS_ADMIN_PASSWORD:?falta REDIS_ADMIN_PASSWORD}"
: "${REDIS_HEALTHCHECK_PASSWORD:?falta REDIS_HEALTHCHECK_PASSWORD}"
: "${REDIS_MONITOR_PASSWORD:?falta REDIS_MONITOR_PASSWORD}"
: "${REDIS_SVC_SERVIYA_COORD_PASSWORD:?falta REDIS_SVC_SERVIYA_COORD_PASSWORD}"
: "${REDIS_SVC_NOTIFICATIONS_COORD_PASSWORD:?falta REDIS_SVC_NOTIFICATIONS_COORD_PASSWORD}"
: "${REDIS_SVC_GATEWAY_COORD_PASSWORD:?falta REDIS_SVC_GATEWAY_COORD_PASSWORD}"
: "${REDIS_CHAT_PASSWORD:?falta REDIS_CHAT_PASSWORD}"

export ADMIN_HASH="$(hash "$REDIS_ADMIN_PASSWORD")"
export HEALTHCHECK_HASH="$(hash "$REDIS_HEALTHCHECK_PASSWORD")"
export MONITOR_HASH="$(hash "$REDIS_MONITOR_PASSWORD")"
export SVC_SERVIYA_COORD_HASH="$(hash "$REDIS_SVC_SERVIYA_COORD_PASSWORD")"
export SVC_NOTIFICATIONS_COORD_HASH="$(hash "$REDIS_SVC_NOTIFICATIONS_COORD_PASSWORD")"
export SVC_GATEWAY_COORD_HASH="$(hash "$REDIS_SVC_GATEWAY_COORD_PASSWORD")"
export CHAT_COORD_HASH="$(hash "$REDIS_CHAT_PASSWORD")"

# El aclfile de Redis NO admite comentarios ni líneas en blanco: cada línea debe empezar por 'user'.
# Por eso, tras sustituir los hashes, descartamos las líneas de comentario (^#) y las vacías; los
# comentarios se conservan en la PLANTILLA versionada (que sí los documenta), no en el ACL generado.
envsubst < "$1" | grep -vE '^[[:space:]]*(#|$)' > "$2"
# 644 (no 640): el archivo se monta en el contenedor, donde Redis corre como el usuario `redis` (uid 999),
# que no es dueño ni grupo del archivo del host; con 640 no puede leerlo y redis-coord no arranca
# ("Error loading ACLs ... Permission denied"). Solo contiene HASHES SHA-256, nunca contraseñas en claro.
chmod 644 "$2"
echo "Generado $2"
