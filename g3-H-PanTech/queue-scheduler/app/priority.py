"""Regla de priorización de la cola de cocina y validación de transiciones.

Todas las funciones de este módulo son puras (no tocan Redis ni PostgreSQL),
lo que permite probarlas con pytest sin infraestructura.
"""

# Transiciones de estado permitidas para una comanda:
#   pendiente -> en_preparacion -> listo -> entregado
ALLOWED_TRANSITIONS = {
    "pendiente": {"en_preparacion"},
    "en_preparacion": {"listo"},
    "listo": {"entregado"},
    "entregado": set(),
}


def effective_minutes(prep_minutes) -> int:
    """Minutos efectivos de un producto.

    Los productos de vitrina cuentan como 1 minuto como mínimo, de modo que
    cualquier valor menor (o inválido) se eleva a 1.
    """
    try:
        value = int(prep_minutes)
    except (TypeError, ValueError):
        value = 1
    return max(value, 1)


def estimate_minutes(items, product_info) -> int:
    """Tiempo estimado de una comanda.

    items: lista de objetos/dicts con ``product_id`` y ``quantity``.
    product_info: dict ``{product_id: {"prep_minutes": int}}`` leído de PostgreSQL.
    """
    total = 0
    for item in items:
        product_id = _get(item, "product_id")
        quantity = int(_get(item, "quantity"))
        info = product_info.get(product_id, {})
        total += effective_minutes(info.get("prep_minutes", 1)) * quantity
    return total


def kitchen_sort_key(order):
    """Clave de orden de la cola de cocina.

    Primero las comandas ``en_preparacion``, luego por tiempo estimado
    (menor primero) y finalmente por hora de llegada (FIFO como desempate).
    """
    status_rank = 0 if order["status"] == "en_preparacion" else 1
    return (status_rank, order["estimated_minutes"], order["arrival_ts"])


def is_valid_transition(current_status: str, new_status: str) -> bool:
    """Indica si ``current_status -> new_status`` es una transición permitida."""
    return new_status in ALLOWED_TRANSITIONS.get(current_status, set())


def _get(obj, key):
    if isinstance(obj, dict):
        return obj[key]
    return getattr(obj, key)
