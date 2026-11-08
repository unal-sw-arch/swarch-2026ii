from fastapi import APIRouter, Depends

from app.repositories.product_repository import ProductRepository, get_product_repo
from app.schemas import MenuItemOut
from app.security import Principal, get_current_principal
from app.services import menu_service

router = APIRouter(tags=["menu"])


@router.get("/menu", response_model=list[MenuItemOut], summary="Menú con precios y tiempos de preparación")
def get_menu(
    _: Principal = Depends(get_current_principal),
    repo: ProductRepository = Depends(get_product_repo),
):
    return menu_service.list_menu(repo)
