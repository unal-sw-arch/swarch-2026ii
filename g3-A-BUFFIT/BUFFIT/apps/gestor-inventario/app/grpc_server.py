from concurrent import futures

import grpc

from app import inventory_pb2, inventory_pb2_grpc
from app.database import get_db
from app.config import Config
from app import inventory_service as svc


class InventoryServicer(inventory_pb2_grpc.InventoryServiceServicer):
    """Conector gRPC Gestor de Pedidos -> Gestor de Inventario (según diagrama C&C)."""

    def ValidateAndDeductStock(self, request, context):
        items = [
            {"product_name": i.product_name, "category": i.category, "quantity": i.quantity or 1}
            for i in request.items
        ]
        try:
            ok, msg = svc.validate_and_deduct(get_db(), request.dining_hall, items)
        except Exception as e:  # p.ej. Mongo caído
            return inventory_pb2.StockCheckResponse(available=False, message=f"Error interno de inventario: {e}")
        return inventory_pb2.StockCheckResponse(available=ok, message=msg)


def build_grpc_server(port=None):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=8))
    inventory_pb2_grpc.add_InventoryServiceServicer_to_server(InventoryServicer(), server)
    bound = server.add_insecure_port(f"[::]:{port if port is not None else Config.GRPC_PORT}")
    return server, bound
