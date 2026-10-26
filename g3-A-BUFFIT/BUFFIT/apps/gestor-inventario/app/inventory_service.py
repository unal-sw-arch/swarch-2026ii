"""Lógica de negocio del Gestor de Inventario (independiente de Flask / gRPC)."""
import re
import unicodedata
from datetime import datetime, timezone

from bson import ObjectId
from bson.errors import InvalidId

# Categorías tal como las consume el Portal (Home.jsx)
CATEGORIES = ["proteinas", "carbohidratos", "ensaladas", "bebidas", "sopas", "postres"]

_ALIASES = {
    "proteina": "proteinas", "proteinas": "proteinas",
    "carbohidrato": "carbohidratos", "carbohidratos": "carbohidratos",
    "ensalada": "ensaladas", "ensaladas": "ensaladas", "verdura": "ensaladas",
    "verduras": "ensaladas",
    "bebida": "bebidas", "bebidas": "bebidas",
    "sopa": "sopas", "sopas": "sopas",
    "postre": "postres", "postres": "postres",
}

DEFAULT_DINING_HALLS = ["Comedor Central", "Comedor Hemeroteca", "Comedor Agronomía"]

DEFAULT_TIME_SLOTS = [  # franjas de 15 min (mismas del Portal)
    "11:30 AM - 11:45 AM",
    "11:45 AM - 12:00 PM",
    "12:00 PM - 12:15 PM",
    "12:15 PM - 12:30 PM",
    "12:45 PM - 01:00 PM",
    "1:15 PM - 1:30 PM",
]

SEED_MENU = {
    "proteinas": [("Pechuga a la plancha", 40), ("Carne desmechada", 35), ("Pescado frito", 25), ("Lentejas guisadas", 30)],
    "carbohidratos": [("Arroz blanco", 80), ("Papa criolla", 60), ("Yuca cocida", 45), ("Plátano maduro", 50)],
    "ensaladas": [("Ensalada fresca", 50), ("Ensalada de repollo", 40)],
    "bebidas": [("Jugo de mora", 70), ("Limonada", 60)],
    "sopas": [("Sopa de verduras", 30), ("Crema de pollo", 25)],
    "postres": [("Flan de leche", 25), ("Fruta de temporada", 35)],
}


class InventoryError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.message = message
        self.status = status


def normalize_category(value):
    if not value:
        return None
    key = unicodedata.normalize("NFKD", str(value)).encode("ascii", "ignore").decode().strip().lower()
    return _ALIASES.get(key)


def _name_filter(name):
    """Coincidencia exacta de nombre, sin distinguir mayúsculas."""
    return {"$regex": f"^{re.escape(name.strip())}$", "$options": "i"}


def _doc(item):
    return {
        "id": str(item["_id"]),
        "dining_hall": item["dining_hall"],
        "category": item["category"],
        "product_name": item["product_name"],
        "quantity": item["quantity"],
        "updated_at": item["updated_at"].isoformat() if item.get("updated_at") else None,
    }


# ---------------------------------------------------------------- consulta
def get_menu_options(db, dining_hall=None):
    """Formato que espera el Portal: listas de nombres por categoría (solo con stock > 0)."""
    query = {"quantity": {"$gt": 0}}
    if dining_hall:
        query["dining_hall"] = _name_filter(dining_hall)
    result = {c: [] for c in CATEGORIES}
    for item in db.items.find(query).sort("product_name", 1):
        names = result[item["category"]]
        if item["product_name"] not in names:
            names.append(item["product_name"])
    halls = sorted(db.items.distinct("dining_hall")) or DEFAULT_DINING_HALLS
    result["puntosRecogida"] = halls
    result["rangosHorarios"] = DEFAULT_TIME_SLOTS
    return result


def get_menu_with_stock(db, dining_hall=None):
    """Menú del día organizado por categoría con cantidad disponible."""
    query = {}
    if dining_hall:
        query["dining_hall"] = _name_filter(dining_hall)
    result = {c: [] for c in CATEGORIES}
    for item in db.items.find(query).sort("product_name", 1):
        result[item["category"]].append(
            {"product_name": item["product_name"], "quantity": item["quantity"], "dining_hall": item["dining_hall"]}
        )
    return result


def list_items(db, dining_hall=None, category=None):
    query = {}
    if dining_hall:
        query["dining_hall"] = _name_filter(dining_hall)
    if category:
        cat = normalize_category(category)
        if not cat:
            raise InventoryError(f"Categoría inválida: {category}")
        query["category"] = cat
    return [_doc(i) for i in db.items.find(query).sort([("dining_hall", 1), ("category", 1), ("product_name", 1)])]


# ----------------------------------------------------------- modificación
def upsert_item(db, data):
    hall = (data.get("dining_hall") or "").strip()
    name = (data.get("product_name") or "").strip()
    category = normalize_category(data.get("category"))
    quantity = data.get("quantity")
    if not hall or not name or not category:
        raise InventoryError("Se requieren dining_hall, product_name y category válida.")
    if not isinstance(quantity, int) or isinstance(quantity, bool) or quantity < 0:
        raise InventoryError("quantity debe ser un entero >= 0.")
    existing = db.items.find_one({"dining_hall": _name_filter(hall), "product_name": _name_filter(name)})
    now = datetime.now(timezone.utc)
    if existing:
        db.items.update_one(
            {"_id": existing["_id"]},
            {"$set": {"quantity": quantity, "category": category, "updated_at": now}},
        )
        return _doc(db.items.find_one({"_id": existing["_id"]})), False
    new = {"dining_hall": hall, "product_name": name, "category": category,
           "quantity": quantity, "updated_at": now}
    new["_id"] = db.items.insert_one(new).inserted_id
    return _doc(new), True


def _oid(item_id):
    try:
        return ObjectId(item_id)
    except (InvalidId, TypeError):
        raise InventoryError("ID de ítem inválido.", 400)


def update_item(db, item_id, data):
    changes = {}
    if "quantity" in data:
        q = data["quantity"]
        if not isinstance(q, int) or isinstance(q, bool) or q < 0:
            raise InventoryError("quantity debe ser un entero >= 0.")
        changes["quantity"] = q
    if "product_name" in data and str(data["product_name"]).strip():
        changes["product_name"] = str(data["product_name"]).strip()
    if "category" in data:
        cat = normalize_category(data["category"])
        if not cat:
            raise InventoryError("Categoría inválida.")
        changes["category"] = cat
    if not changes:
        raise InventoryError("Nada que actualizar (quantity, product_name o category).")
    changes["updated_at"] = datetime.now(timezone.utc)
    res = db.items.update_one({"_id": _oid(item_id)}, {"$set": changes})
    if res.matched_count == 0:
        raise InventoryError("Ítem no encontrado.", 404)
    return _doc(db.items.find_one({"_id": _oid(item_id)}))


def delete_item(db, item_id):
    if db.items.delete_one({"_id": _oid(item_id)}).deleted_count == 0:
        raise InventoryError("Ítem no encontrado.", 404)


# ------------------------------------------------- descuento / devolución
def _aggregate(items):
    """Junta ítems repetidos (p.ej. el mismo carbohidrato elegido dos veces)."""
    agg = {}
    for it in items:
        name = (it.get("product_name") or "").strip()
        qty = it.get("quantity", 1)
        if not name or not isinstance(qty, int) or qty <= 0:
            raise InventoryError(f"Ítem inválido en el pedido: {it}")
        key = name.lower()
        if key in agg:
            agg[key][1] += qty
        else:
            agg[key] = [name, qty]
    return list(agg.values())


def validate_and_deduct(db, dining_hall, items):
    """
    Valida existencias y descuenta. Mongo standalone no tiene transacciones,
    así que se descuenta con filtro condicional y se revierte si algo falla.
    Devuelve (ok, mensaje).
    """
    try:
        needed = _aggregate(items)
    except InventoryError as e:
        return False, e.message
    if not needed:
        return False, "El pedido no contiene ítems."

    hall_filter = _name_filter(dining_hall or "")
    # 1) validación previa (mensajes claros)
    for name, qty in needed:
        doc = db.items.find_one({"dining_hall": hall_filter, "product_name": _name_filter(name)})
        if not doc:
            return False, f"'{name}' no está en el inventario de {dining_hall}."
        if doc["quantity"] < qty:
            return False, f"Stock insuficiente de '{name}' en {dining_hall} (disponible: {doc['quantity']}, solicitado: {qty})."

    # 2) descuento condicional con reversión
    applied = []
    now = datetime.now(timezone.utc)
    for name, qty in needed:
        res = db.items.update_one(
            {"dining_hall": hall_filter, "product_name": _name_filter(name), "quantity": {"$gte": qty}},
            {"$inc": {"quantity": -qty}, "$set": {"updated_at": now}},
        )
        if res.modified_count == 0:  # alguien más lo consumió entre medias
            for rname, rqty in applied:
                db.items.update_one(
                    {"dining_hall": hall_filter, "product_name": _name_filter(rname)},
                    {"$inc": {"quantity": rqty}},
                )
            return False, f"Stock insuficiente de '{name}' en {dining_hall}."
        applied.append((name, qty))
    return True, "Inventario descontado correctamente."


def restore_stock(db, dining_hall, items):
    """Devuelve porciones al inventario (pedido cancelado)."""
    needed = _aggregate(items)
    hall_filter = _name_filter(dining_hall or "")
    restored = 0
    for name, qty in needed:
        res = db.items.update_one(
            {"dining_hall": hall_filter, "product_name": _name_filter(name)},
            {"$inc": {"quantity": qty}, "$set": {"updated_at": datetime.now(timezone.utc)}},
        )
        restored += res.modified_count
    return restored


# ------------------------------------------------------------- reportes
def create_leftover_report(db, dining_hall):
    """Informe de sobrantes al cierre de jornada del comedor (guarda una foto del stock)."""
    items = [i for i in db.items.find({"dining_hall": _name_filter(dining_hall), "quantity": {"$gt": 0}})
             .sort([("category", 1), ("product_name", 1)])]
    report = {
        "dining_hall": dining_hall,
        "created_at": datetime.now(timezone.utc),
        "leftovers": [{"category": i["category"], "product_name": i["product_name"], "quantity": i["quantity"]} for i in items],
        "total_portions": sum(i["quantity"] for i in items),
    }
    report["_id"] = db.leftover_reports.insert_one(report).inserted_id
    return _report(report)


def _report(r):
    return {
        "id": str(r["_id"]),
        "dining_hall": r["dining_hall"],
        "created_at": r["created_at"].isoformat(),
        "total_portions": r["total_portions"],
        "leftovers": r["leftovers"],
    }


def list_leftover_reports(db, dining_hall=None):
    q = {"dining_hall": _name_filter(dining_hall)} if dining_hall else {}
    return [_report(r) for r in db.leftover_reports.find(q).sort("created_at", -1).limit(30)]


# ------------------------------------------------------------------ seed
def seed_demo_data(db, reset=False):
    if reset:
        db.items.delete_many({})
    if db.items.count_documents({}) > 0:
        return 0
    now = datetime.now(timezone.utc)
    docs = []
    for i, hall in enumerate(DEFAULT_DINING_HALLS):
        for category, products in SEED_MENU.items():
            for name, qty in products:
                # variación por comedor para que no queden idénticos
                docs.append({"dining_hall": hall, "category": category, "product_name": name,
                             "quantity": max(5, qty - i * 7), "updated_at": now})
    db.items.insert_many(docs)
    return len(docs)
