import grpc
from app import inventory_pb2, inventory_pb2_grpc
from app.config import Config

INVENTORY_GRPC_HOST = getattr(Config, "INVENTORY_GRPC_HOST", "localhost:50051")

def check_and_reserve_inventory(dining_hall: str, items_data: list) -> tuple[bool, str]:
    """
    Envía una petición gRPC síncrona al Gestor de Inventario para consultar 
    y descontar las porciones seleccionadas.
    """
    try:
        with grpc.insecure_channel(INVENTORY_GRPC_HOST) as channel:
            stub = inventory_pb2_grpc.InventoryServiceStub(channel)
            
            # Convierte la lista de ítems en mensajes de Protobuf
            items_payload = [
                inventory_pb2.ItemQuantity(
                    product_name=item.get("product_name") or item.get("name"),
                    category=item.get("category") or item.get("item_type", ""),
                    quantity=item.get("quantity", 1)
                ) for item in items_data
            ]
            
            request = inventory_pb2.StockCheckRequest(
                dining_hall=dining_hall,
                items=items_payload
            )
            
            # Invocación remota síncrona (con timeout de 3 segundos)
            response = stub.ValidateAndDeductStock(request, timeout=3.0)
            return response.available, response.message

    except grpc.RpcError as e:
        return False, f"Error de comunicación gRPC con Gestor de Inventario: {e.details() if hasattr(e, 'details') else str(e)}"
    except Exception as e:
        return False, f"Excepción inesperada al consultar inventario: {str(e)}"