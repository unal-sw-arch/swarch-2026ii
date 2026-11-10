#!/usr/bin/env python3
"""Smoke test end-to-end de Panadería Express (Prototipo 2).

Flujo: login -> menú -> pedido -> pago -> cocina (en preparación/listo) ->
entrega -> historial -> reserva -> reportes.

Solo usa la biblioteca estándar. Requiere el compose P2 levantado y el Worker
consumiendo eventos. El script habla únicamente con el API Gateway; nunca llama
a los servicios internos ni usa X-Service-Token.

Uso:
    python scripts/smoke_test.py
    python scripts/smoke_test.py --base-url http://localhost:8080 --timeout 180
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any, Callable

PASSWORD = "Demo1234!"
CLIENT = "cliente@demo.com"
COOK = "cocinero@demo.com"
DISPATCHER = "despachador@demo.com"
ADMIN = "admin@demo.com"


class SmokeError(RuntimeError):
    pass


class Client:
    def __init__(self, base_url: str, timeout: float = 20.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        token: str | None = None,
        body: Any = None,
        idempotency_key: str | None = None,
    ) -> tuple[int, Any]:
        data = None
        headers = {"accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["content-type"] = "application/json"
        if token:
            headers["authorization"] = f"Bearer {token}"
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key

        request = urllib.request.Request(
            f"{self.base_url}{path}", data=data, headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                status = response.status
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            text = raw.decode("utf-8", "replace")
            raise SmokeError(f"{method} {path} -> HTTP {exc.code}: {text[:500]}") from exc
        except urllib.error.URLError as exc:
            raise SmokeError(f"{method} {path} -> no se pudo conectar: {exc.reason}") from exc

        if not raw:
            return status, None
        text = raw.decode("utf-8", "replace")
        try:
            return status, json.loads(text)
        except json.JSONDecodeError:
            return status, text

    def login(self, email: str, password: str = PASSWORD) -> str:
        _, body = self.request("POST", "/auth/login", body={"email": email, "password": password})
        if not isinstance(body, dict) or not body.get("access_token"):
            raise SmokeError(f"login de {email} no devolvió access_token")
        return str(body["access_token"])

    def wait_for(
        self,
        description: str,
        fn: Callable[[], Any],
        *,
        timeout: float,
        interval: float = 2.0,
    ) -> Any:
        deadline = time.monotonic() + timeout
        last_error: Exception | None = None
        while time.monotonic() < deadline:
            try:
                result = fn()
                if result:
                    return result
            except SmokeError as exc:
                last_error = exc
            time.sleep(interval)
        suffix = f" (último error: {last_error})" if last_error else ""
        raise SmokeError(f"Se agotó el tiempo esperando: {description}{suffix}")

    def health(self) -> None:
        self.wait_for(
            "API Gateway /health",
            lambda: self.request("GET", "/health")[0] == 200,
            timeout=60,
            interval=2,
        )


def check(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def run(client: Client, timeout: float, skip_reservation: bool) -> None:
    print("1/8 Esperando al API Gateway…")
    client.health()

    print("2/8 Login de los usuarios demo…")
    client_token = client.login(CLIENT)
    cook_token = client.login(COOK)
    dispatcher_token = client.login(DISPATCHER)
    admin_token = client.login(ADMIN)
    check(bool(client_token and cook_token and dispatcher_token and admin_token), "login demo")

    print("3/8 Consultando el menú…")
    _, menu = client.request("GET", "/menu", token=client_token)
    check(isinstance(menu, list) and bool(menu), "el menú está vacío")
    product = menu[0]
    product_id = product["product_id"]
    print(f"   producto elegido: {product['name']} ({product_id})")

    print("4/8 Creando pedido y pagando…")
    _, order = client.request(
        "POST",
        "/orders",
        token=client_token,
        body={"order_type": "para_llevar", "items": [{"product_id": product_id, "quantity": 1}]},
    )
    check(isinstance(order, dict) and order.get("id"), f"pedido inválido: {order}")
    order_id = str(order["id"])
    _, paid = client.request(
        "POST",
        f"/orders/{order_id}/payment",
        token=client_token,
        idempotency_key=f"smoke-{uuid.uuid4()}",
        body={"amount": str(order["total"]), "method": "simulado"},
    )
    check(isinstance(paid, dict) and paid.get("status") == "confirmado", f"pago inválido: {paid}")
    print(f"   pedido {order_id} pagado por {order['total']}")

    print("5/8 Llevando el pedido por cocina y entrega…")

    def queue_item() -> dict | None:
        _, queue = client.request("GET", "/queue", token=cook_token)
        if not isinstance(queue, dict):
            return None
        for item in queue.get("items", []):
            if item.get("order_id") == order_id:
                return item
        return None

    client.wait_for("la comanda llegue a Kitchen", queue_item, timeout=timeout)
    client.request(
        "PATCH",
        f"/queue/orders/{order_id}/status",
        token=cook_token,
        body={"status": "en_preparacion"},
    )
    client.request(
        "PATCH",
        f"/queue/orders/{order_id}/status",
        token=cook_token,
        body={"status": "listo"},
    )
    client.wait_for(
        "el pedido quede listo en Order Service",
        lambda: client.request("GET", f"/orders/{order_id}", token=client_token)[1].get("status")
        == "listo",
        timeout=timeout,
    )
    delivered, _ = client.request(
        "POST", f"/queue/orders/{order_id}/deliver", token=dispatcher_token
    )
    check(delivered in (200, 201), f"entrega respondió {delivered}")

    print("6/8 Verificando historial…")

    def history_item() -> dict | None:
        _, history = client.request("GET", "/history?page_size=100", token=admin_token)
        if not isinstance(history, dict):
            return None
        for item in history.get("items", []):
            if item.get("order_id") == order_id:
                return item
        return None

    item = client.wait_for("el pedido aparezca en el historial", history_item, timeout=timeout)
    check(item.get("status") in ("pagado", "en_preparacion", "listo", "entregado"), f"historial: {item}")
    print(f"   historial OK: {item.get('status')}")

    if skip_reservation:
        print("7/8 Reserva omitida por --skip-reservation")
    else:
        print("7/8 Creando y cancelando una reserva…")
        reservation_date = (dt.date.today() + dt.timedelta(days=2)).isoformat()
        _, reservation = client.request(
            "POST",
            "/reservations",
            token=client_token,
            body={
                "date": reservation_date,
                "time": "10:00",
                "people": 2,
                "notes": "Smoke test",
            },
        )
        check(isinstance(reservation, dict) and reservation.get("id"), f"reserva inválida: {reservation}")
        reservation_id = reservation["id"]
        _, cancelled = client.request(
            "PUT",
            f"/reservations/{reservation_id}",
            token=client_token,
            body={"status": "cancelada", "reason": "Smoke test"},
        )
        check(isinstance(cancelled, dict) and cancelled.get("status") == "cancelada", "no se canceló")
        print(f"   reserva {reservation_id} cancelada")

    print("8/8 Consultando reportes…")
    today = dt.date.today().isoformat()
    start = (dt.date.today() - dt.timedelta(days=30)).isoformat()
    query = urllib.parse.urlencode({"from": start, "to": today})
    _, sales = client.request("GET", f"/reports/sales?{query}", token=admin_token)
    check(isinstance(sales, dict) and sales.get("orders_count") is not None, f"reporte inválido: {sales}")
    _, times = client.request("GET", f"/reports/times?{query}", token=admin_token)
    check(isinstance(times, dict) and "avg_prep_minutes" in times, f"tiempos inválidos: {times}")

    print("\nSMOKE TEST OK")
    print(f"  pedido: {order_id}")
    print(f"  estado final Order Service: listo")
    print(f"  entrega Kitchen: confirmada")
    print(f"  historial y reportes: OK")


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test E2E de Panadería Express P2")
    parser.add_argument("--base-url", default="http://localhost:8080", help="URL del API Gateway")
    parser.add_argument("--timeout", type=float, default=120, help="Segundos por espera asíncrona")
    parser.add_argument("--skip-reservation", action="store_true", help="Omitir el flujo de reservas")
    args = parser.parse_args()

    try:
        run(Client(args.base_url), args.timeout, args.skip_reservation)
    except SmokeError as exc:
        print(f"\nSMOKE TEST FALLÓ: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
