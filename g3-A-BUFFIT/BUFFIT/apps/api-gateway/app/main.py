from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import users, orders, inventory  # <-- Correcto mapeo C&C
from app.config import settings

app = FastAPI(
    title="BUFF/T API Gateway",
    description="Orquestador centralizador de peticiones para BUFF/T",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Conectores definidos en el diagrama C&C
app.include_router(users.router)      # userPort -> Gestor de Usuarios
app.include_router(orders.router)     # orderPort -> Gestor de Pedidos
app.include_router(inventory.router)  # inventoryPort -> Gestor de Inventario

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "API Gateway (FastAPI)"}