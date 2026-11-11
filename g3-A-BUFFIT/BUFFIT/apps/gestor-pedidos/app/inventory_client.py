import requests

from app.config import Config


def restock_inventory(dining_hall: str, items) -> tuple[bool, str]:
    """Devuelve porciones al Gestor de Inventario (pedido cancelado) vía HTTP /restock."""
    payload = {
        "dining_hall": dining_hall,
        "items": [{"product_name": i.product_name, "quantity": i.quantity or 1} for i in items],
    }
    try:
        resp = requests.post(f"{Config.INVENTORY_HTTP_URL}/restock", json=payload, timeout=5.0)
        if resp.status_code != 200:
            return False, f"Inventario respondió {resp.status_code}"
        return True, "Stock devuelto"
    except requests.RequestException as e:
        return False, f"No se pudo contactar al inventario: {e}"
