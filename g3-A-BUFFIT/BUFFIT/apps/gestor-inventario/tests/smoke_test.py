"""Prueba rápida sin Mongo real:  pip install mongomock httpx  y luego  python tests/smoke_test.py"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import grpc, jwt, mongomock
from fastapi.testclient import TestClient
from app.main import create_app
from app import inventory_pb2, inventory_pb2_grpc
from app.config import Config
from app.grpc_server import build_grpc_server

app = create_app(mongo_client=mongomock.MongoClient(), start_grpc=False)
tok = lambda role: {"Authorization": "Bearer " + jwt.encode({"sub": "1", "role": role}, Config.JWT_SECRET_KEY, algorithm=Config.JWT_ALGORITHM)}

with TestClient(app) as c:  # el "with" ejecuta el arranque (índices + datos de ejemplo)
    # --- lectura
    r = c.get("/opciones", params={"comedor": "Comedor Central"}); d = r.json()
    assert r.status_code == 200 and "Pechuga a la plancha" in d["proteinas"] and d["puntosRecogida"], d
    print("opciones OK:", {k: len(v) for k, v in d.items()})
    assert c.get("/inventario/opciones").status_code == 200  # alias
    assert c.get("/health").status_code == 200

    # --- permisos
    assert c.post("/items", json={"dining_hall": "x", "product_name": "y", "category": "z", "quantity": 1}).status_code == 401
    body = {"dining_hall": "Comedor Central", "product_name": "Arepa", "category": "Carbohidrato", "quantity": 10}
    assert c.post("/items", json=body, headers=tok("Usuario")).status_code == 403
    r = c.post("/items", json=body, headers=tok("Comedor")); assert r.status_code == 201, r.json()
    item_id = r.json()["id"]
    r = c.put(f"/items/{item_id}", json={"quantity": 7}, headers=tok("Comedor")); assert r.json()["quantity"] == 7
    assert c.put(f"/items/{item_id}", json={"quantity": -1}, headers=tok("Comedor")).status_code == 422
    print("CRUD + roles OK")

    # --- gRPC igual que lo llama Gestor de Pedidos
    server, port = build_grpc_server(0); server.start()
    def call(hall, items):
        with grpc.insecure_channel(f"localhost:{port}") as ch:
            stub = inventory_pb2_grpc.InventoryServiceStub(ch)
            req = inventory_pb2.StockCheckRequest(dining_hall=hall, items=[inventory_pb2.ItemQuantity(product_name=n, category=cat, quantity=q) for n, cat, q in items])
            r = stub.ValidateAndDeductStock(req, timeout=3); return r.available, r.message
    def stock(name): return next(i["quantity"] for i in c.get("/items", params={"comedor": "Comedor Central"}).json() if i["product_name"] == name)

    before = stock("Arroz blanco")
    ok, msg = call("Comedor Central", [("Pechuga a la plancha", "Proteína", 1), ("Arroz blanco", "Carbohidrato", 1), ("Arroz blanco", "Carbohidrato", 1)])
    assert ok and stock("Arroz blanco") == before - 2, msg
    print("gRPC descuento OK (mismo carbohidrato x2 =", before - stock("Arroz blanco"), ")")
    ok, msg = call("Comedor Central", [("Pechuga a la plancha", "Proteína", 1), ("Arepa", "Carbohidrato", 999)])
    assert not ok and stock("Arepa") == 7; print("gRPC rechazo OK ->", msg)
    ok, msg = call("Comedor Central", [("Plato inventado", "x", 1)]); assert not ok; print("gRPC inexistente OK ->", msg)
    p = stock("Pechuga a la plancha")
    c.post("/restock", json={"dining_hall": "Comedor Central", "items": [{"product_name": "Pechuga a la plancha", "quantity": 1}]})
    assert stock("Pechuga a la plancha") == p + 1; print("restock OK")

    # --- reporte
    r = c.post("/reportes/sobrantes", json={"dining_hall": "Comedor Central"}, headers=tok("Comedor")); assert r.status_code == 201 and r.json()["total_portions"] > 0
    assert len(c.get("/reportes/sobrantes").json()) == 1; print("reporte sobrantes OK")
    server.stop(0)
print("\nTODO OK")
