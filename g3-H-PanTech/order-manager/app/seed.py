"""Datos semilla: al menos 8 productos con tiempos de preparación realistas."""
from decimal import Decimal

from sqlalchemy.orm import Session

from .models import Product

SEED_PRODUCTS = [
    # Vitrina / café: rápidos (1 minuto).
    {"name": "Café americano", "category": "vitrina", "prep_minutes": 1, "price": Decimal("3.50")},
    {"name": "Café con leche", "category": "vitrina", "prep_minutes": 1, "price": Decimal("4.00")},
    {"name": "Pan croissant", "category": "vitrina", "prep_minutes": 1, "price": Decimal("2.50")},
    {"name": "Pan de queso", "category": "vitrina", "prep_minutes": 1, "price": Decimal("2.00")},
    # Preparados: toman tiempo.
    {"name": "Omelette de queso", "category": "preparado", "prep_minutes": 12, "price": Decimal("12.00")},
    {"name": "Huevos revueltos", "category": "preparado", "prep_minutes": 10, "price": Decimal("11.00")},
    {"name": "Sándwich de pollo", "category": "preparado", "prep_minutes": 8, "price": Decimal("13.00")},
    {"name": "Calentado paisa", "category": "preparado", "prep_minutes": 15, "price": Decimal("15.00")},
]


def seed_products(db: Session) -> int:
    """Inserta los productos semilla si la tabla está vacía. Devuelve cuántos insertó."""
    if db.query(Product).count() > 0:
        return 0
    for data in SEED_PRODUCTS:
        db.add(Product(**data))
    db.commit()
    return len(SEED_PRODUCTS)
