from typing import Optional

from fastapi import APIRouter, Depends, Query
from fastapi.responses import JSONResponse

from app.database import get_db
from app.schemas import ItemIn, ItemUpdate, RestockIn, ReportIn
from app.security import require_write_role
from app import inventory_service as svc

router = APIRouter(tags=["Gestor de Inventario"])


# ------------------------------------------------------------ lectura
@router.get("/opciones")
def opciones(comedor: Optional[str] = None):
    """Opciones del menú del día para el Portal (solo productos con stock > 0)."""
    return svc.get_menu_options(get_db(), comedor)


@router.get("/menu")
def menu(comedor: Optional[str] = None):
    """Menú organizado por categoría con la cantidad disponible."""
    return svc.get_menu_with_stock(get_db(), comedor)


@router.get("/items")
def items(comedor: Optional[str] = None, categoria: Optional[str] = None):
    """Inventario completo (filtrable por comedor y categoría)."""
    return svc.list_items(get_db(), comedor, categoria)


# ------------------------------------------------------- modificación
@router.post("/items", dependencies=[Depends(require_write_role)])
def create_or_set_item(body: ItemIn):
    """Crea el ítem, o fija su cantidad si ya existe en ese comedor."""
    item, created = svc.upsert_item(get_db(), body.model_dump())
    return JSONResponse(item, status_code=201 if created else 200)


@router.put("/items/{item_id}", dependencies=[Depends(require_write_role)])
def update_item(item_id: str, body: ItemUpdate):
    return svc.update_item(get_db(), item_id, body.model_dump(exclude_unset=True, exclude_none=True))


@router.delete("/items/{item_id}", dependencies=[Depends(require_write_role)])
def delete_item(item_id: str):
    svc.delete_item(get_db(), item_id)
    return {"message": "Ítem eliminado"}


@router.post("/restock")
def restock(body: RestockIn):
    """Devuelve porciones al inventario cuando un pedido se cancela."""
    restored = svc.restore_stock(get_db(), body.dining_hall, [i.model_dump() for i in body.items])
    return {"restored_items": restored}


# ------------------------------------------------------------ reportes
@router.post("/reportes/sobrantes", status_code=201, dependencies=[Depends(require_write_role)])
def create_report(body: ReportIn):
    """Informe de sobrantes al cierre de jornada del comedor."""
    return svc.create_leftover_report(get_db(), body.dining_hall)


@router.get("/reportes/sobrantes")
def list_reports(comedor: Optional[str] = None):
    return svc.list_leftover_reports(get_db(), comedor)


# --------------------------------------------------------------- demo
@router.post("/seed", dependencies=[Depends(require_write_role)])
def seed(reset: bool = Query(False)):
    """Recarga datos de demostración (reset=true borra el inventario actual)."""
    return {"inserted": svc.seed_demo_data(get_db(), reset=reset)}
