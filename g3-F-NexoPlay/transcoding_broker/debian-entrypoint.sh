#!/bin/sh
set -eu
: "${RABBITMQ_DEFAULT_USER:?Missing RabbitMQ user}"
: "${RABBITMQ_DEFAULT_PASS:?Missing RabbitMQ password}"
vhost="${RABBITMQ_DEFAULT_VHOST:-/}"
rabbitmq-server &
server_pid=$!
trap 'kill -TERM "$server_pid" 2>/dev/null || true; wait "$server_pid" || true' TERM INT EXIT
until rabbitmq-diagnostics -q check_running >/dev/null 2>&1; do
    kill -0 "$server_pid" 2>/dev/null || exit 1
    sleep 1
done
if ! rabbitmqctl -q list_vhosts | grep -Fxq "$vhost"; then
    rabbitmqctl add_vhost "$vhost" >/dev/null
fi
if ! rabbitmqctl -q list_users | cut -f1 | grep -Fxq "$RABBITMQ_DEFAULT_USER"; then
    rabbitmqctl add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS" >/dev/null
fi
rabbitmqctl set_permissions -p "$vhost" "$RABBITMQ_DEFAULT_USER" '.*' '.*' '.*' >/dev/null
rabbitmqctl set_user_tags "$RABBITMQ_DEFAULT_USER" administrator >/dev/null
if [ "$RABBITMQ_DEFAULT_USER" != guest ]; then
    rabbitmqctl delete_user guest >/dev/null 2>&1 || true
fi
wait "$server_pid"
