from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.database import get_db, init_indexes
from app.routes import router
from app import inventory_service as svc


def create_app(mongo_client=None, seed=True, start_grpc=True):
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        db = get_db(mongo_client)
        init_indexes(db)
        if seed:
            svc.seed_demo_data(db)  # solo si la colección está vacía
        grpc_server = None
        if start_grpc:
            from app.grpc_server import build_grpc_server
            from app.config import Config
            grpc_server, _ = build_grpc_server()
            grpc_server.start()
            print(f"[Inventario] gRPC escuchando en :{Config.GRPC_PORT}")
        yield
        if grpc_server:
            grpc_server.stop(grace=2)

    app = FastAPI(
        title="BUFF/T Gestor de Inventario",
        description="Inventario por comedor (HTTP para el Gateway, gRPC para el Gestor de Pedidos)",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

    @app.exception_handler(svc.InventoryError)
    async def inventory_error_handler(request: Request, exc: svc.InventoryError):
        return JSONResponse({"error": exc.message}, status_code=exc.status)

    app.include_router(router)
    # Alias por si el Portal/gateway reenvían la ruta completa /inventario/...
    app.include_router(router, prefix="/inventario", include_in_schema=False)

    @app.get("/health")
    def health_check():
        return {"status": "ok", "service": "Gestor de Inventario (FastAPI + gRPC)"}

    return app


app = create_app()
