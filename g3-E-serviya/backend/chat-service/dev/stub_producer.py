#!/usr/bin/env python3
"""Productor STUB de los eventos que el chat consume, mientras el monolito no los publique (ESTADO CHAT-1).

Publica por la API HTTP de administración de RabbitMQ (puerto 15672), así que no necesita librerías AMQP.
El sobre es el de la guía del chat §9.1.

    python3 stub_producer.py request-created --request 10 --client 1001 --offerer 1002
    python3 stub_producer.py status-changed  --request 10 --status CANCELLED
    python3 stub_producer.py user-registered --user 1001 --name "Ana Cliente"
    python3 stub_producer.py user-suspended  --user 1001        # también: user-reactivated, user-deleted
    python3 stub_producer.py user-updated    --user 1001 --name "Ana María"

Opciones comunes: --event-id (reusar uno para probar la idempotencia), --occurred-at (ISO-8601, para probar
eventos fuera de orden), --rabbit http://localhost:15672, --user-name/--password (por defecto RABBITMQ_USER y
RABBITMQ_PASSWORD del entorno).
"""
import argparse
import base64
import datetime
import json
import os
import urllib.request
import uuid


def publish(args, exchange, routing_key, data):
    envelope = {
        "event_id": args.event_id or str(uuid.uuid4()),
        "event_type": routing_key,
        "event_version": 1,
        "occurred_at": args.occurred_at or datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "producer": "stub-monolith",
        "correlation_id": str(uuid.uuid4()),
        "data": data,
    }
    body = json.dumps({
        "properties": {"content_type": "application/json", "delivery_mode": 2, "message_id": envelope["event_id"]},
        "routing_key": routing_key,
        "payload": json.dumps(envelope),
        "payload_encoding": "string",
    }).encode()
    vhost = urllib.request.quote(args.vhost, safe="")
    req = urllib.request.Request(f"{args.rabbit}/api/exchanges/{vhost}/{exchange}/publish", data=body, method="POST")
    token = base64.b64encode(f"{args.user_name}:{args.password}".encode()).decode()
    req.add_header("Authorization", f"Basic {token}")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req) as resp:
        routed = json.load(resp).get("routed")
    print(f"{routing_key} event_id={envelope['event_id']} routed={routed}")
    if not routed:
        print("  ⚠ nadie recibió el evento: ¿está arriba chat-service? (declara sus colas al arrancar)")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--rabbit", default=os.environ.get("RABBITMQ_MGMT_URL", "http://localhost:15672"))
    p.add_argument("--vhost", default="/")
    p.add_argument("--user-name", default=os.environ.get("RABBITMQ_USER", "serviya"))
    p.add_argument("--password", default=os.environ.get("RABBITMQ_PASSWORD", ""))
    p.add_argument("--event-id")
    p.add_argument("--occurred-at")
    sub = p.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("request-created")
    c.add_argument("--request", type=int, required=True)
    c.add_argument("--client", type=int, required=True)
    c.add_argument("--offerer", type=int, required=True)
    c.add_argument("--service", type=int, default=1)
    c.add_argument("--status", default="PENDING")
    s = sub.add_parser("status-changed")
    s.add_argument("--request", type=int, required=True)
    s.add_argument("--status", required=True)
    s.add_argument("--previous-status")
    for name in ("user-registered", "user-updated"):
        u = sub.add_parser(name)
        u.add_argument("--user", type=int, required=True)
        u.add_argument("--name", required=name == "user-registered")
        u.add_argument("--avatar")
    for name in ("user-suspended", "user-reactivated", "user-deleted"):
        sub.add_parser(name).add_argument("--user", type=int, required=True)
    args = p.parse_args()

    if args.cmd == "request-created":
        publish(args, "serviya.requests", "requests.service_request.created",
                {"request_id": args.request, "client_id": args.client, "offerer_id": args.offerer,
                 "service_id": args.service, "status": args.status})
    elif args.cmd == "status-changed":
        publish(args, "serviya.requests", "requests.service_request.status_changed",
                {"request_id": args.request, "previous_status": args.previous_status, "status": args.status})
    else:
        data = {"user_id": args.user}
        if args.cmd in ("user-registered", "user-updated"):
            if args.name is not None:
                data["display_name"] = args.name
            if args.avatar is not None:
                data["avatar_url"] = args.avatar
        publish(args, "serviya.users", "users.user." + args.cmd.removeprefix("user-"), data)


if __name__ == "__main__":
    main()
