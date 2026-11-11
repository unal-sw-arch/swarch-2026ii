#!/usr/bin/env python3
"""Siembra idempotente de usuarios y productos demo.

Identity Service ya crea los usuarios demo con Flyway y Order Service siembra
el menú si MongoDB está vacío. Este script verifica ambas cosas y completa lo
que falte usando el API Gateway, sin escribir directo en las bases.

Uso:
    python scripts/seed_demo.py
    python scripts/seed_demo.py --base-url http://localhost:8080
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from typing import Any

PASSWORD = "Demo1234!"
ADMIN_EMAIL = "admin@demo.com"

USERS = {
    "cliente@demo.com": ("Cliente Demo", ["cliente"]),
    "mesero@demo.com": ("Mesero Demo", ["mesero"]),
    "cocinero@demo.com": ("Cocinero Demo", ["cocinero"]),
    "despachador@demo.com": ("Despachador Demo", ["despachador"]),
    "supervisor@demo.com": ("Supervisor Demo", ["supervisor"]),
    "admin@demo.com": ("Administrador Demo", ["admin"]),
    "multi@demo.com": ("Mesero y Despachador Demo", ["mesero", "despachador"]),
}

PRODUCTS = [
    ("Café americano", "vitrina", 1, "3.50"),
    ("Café con leche", "vitrina", 1, "4.00"),
    ("Pan croissant", "vitrina", 1, "2.50"),
    ("Pan de queso", "vitrina", 1, "2.00"),
    ("Omelette de queso", "preparado", 12, "12.00"),
    ("Huevos revueltos", "preparado", 10, "11.00"),
    ("Sándwich de pollo", "preparado", 8, "13.00"),
    ("Calentado paisa", "preparado", 15, "15.00"),
]


class Client:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def request(self, method: str, path: str, token: str | None = None, body: Any = None):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"accept": "application/json"}
        if data is not None:
            headers["content-type"] = "application/json"
        if token:
            headers["authorization"] = f"Bearer {token}"
        request = urllib.request.Request(
            f"{self.base_url}{path}", data=data, headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            raise RuntimeError(f"{method} {path} -> HTTP {exc.code}: {raw[:400]}") from exc

    def login(self, email: str) -> str:
        _, body = self.request("POST", "/auth/login", body={"email": email, "password": PASSWORD})
        return str(body["access_token"])


def seed_users(client: Client, admin_token: str) -> None:
    _, users = client.request("GET", "/users", token=admin_token)
    by_email = {str(user["email"]).lower(): user for user in users}

    for email, (full_name, roles) in USERS.items():
        user = by_email.get(email)
        if user is None:
            status, created = client.request(
                "POST",
                "/users",
                token=admin_token,
                body={"email": email, "full_name": full_name, "password": PASSWORD, "roles": roles},
            )
            print(f"  usuario creado: {email} ({status})")
            user = created
        else:
            print(f"  usuario existente: {email}")

        existing = {str(role) for role in user.get("roles", [])}
        for role in roles:
            if role not in existing:
                client.request(
                    "POST",
                    f"/users/{user['id']}/roles",
                    token=admin_token,
                    body={"role": role},
                )
                print(f"    rol agregado: {email} -> {role}")


def seed_products(client: Client, admin_token: str) -> None:
    _, products = client.request("GET", "/products", token=admin_token)
    by_name = {str(product["name"]).strip().lower(): product for product in products}

    for name, category, prep_minutes, price in PRODUCTS:
        if name.lower() in by_name:
            print(f"  producto existente: {name}")
            continue
        status, _ = client.request(
            "POST",
            "/products",
            token=admin_token,
            body={
                "name": name,
                "category": category,
                "prep_minutes": prep_minutes,
                "price": price,
                "active": True,
            },
        )
        print(f"  producto creado: {name} ({status})")


def main() -> int:
    parser = argparse.ArgumentParser(description="Siembra usuarios y productos demo de Panadería Express")
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()

    client = Client(args.base_url)
    try:
        print("Login como admin…")
        admin_token = client.login(ADMIN_EMAIL)
        print("Usuarios demo:")
        seed_users(client, admin_token)
        print("Productos demo:")
        seed_products(client, admin_token)
    except Exception as exc:  # noqa: BLE001
        print(f"SEED FALLÓ: {exc}", file=sys.stderr)
        return 1
    print("\nSEED OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
