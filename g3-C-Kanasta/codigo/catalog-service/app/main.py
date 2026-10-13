from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.database import get_connection

app = FastAPI(title="Kanasta Catalog Service")

PRODUCT_COLUMNS = """
    p.id, p.name, p.description, p.price, p.stock, p.image,
    c.slug AS category, c.name AS category_name
"""


class StockRequest(BaseModel):
    quantity: int


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/api/categories")
def list_categories():
    with get_connection() as conn:
        return conn.execute("SELECT id, name, slug FROM categories ORDER BY name").fetchall()


@app.get("/api/products")
def list_products(search: str = "", category: str = ""):
    query = f"SELECT {PRODUCT_COLUMNS} FROM products p JOIN categories c ON c.id = p.category_id WHERE 1 = 1"
    params = []
    if search:
        query += " AND (p.name ILIKE %s OR p.description ILIKE %s)"
        params += [f"%{search}%", f"%{search}%"]
    if category:
        query += " AND c.slug = %s"
        params.append(category)
    query += " ORDER BY p.name"

    with get_connection() as conn:
        return conn.execute(query, params).fetchall()


@app.get("/api/products/{product_id}")
def get_product(product_id: int):
    with get_connection() as conn:
        product = conn.execute(
            f"SELECT {PRODUCT_COLUMNS} FROM products p JOIN categories c ON c.id = p.category_id WHERE p.id = %s",
            [product_id],
        ).fetchone()
    if product is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return product


# Used by the Orders Service when an order is placed
@app.post("/api/products/{product_id}/reserve")
def reserve_stock(product_id: int, body: StockRequest):
    if body.quantity <= 0:
        raise HTTPException(status_code=400, detail="La cantidad debe ser mayor que cero")

    with get_connection() as conn:
        product = conn.execute(
            "UPDATE products SET stock = stock - %s WHERE id = %s AND stock >= %s RETURNING id, name, stock",
            [body.quantity, product_id, body.quantity],
        ).fetchone()
    if product is None:
        raise HTTPException(status_code=409, detail="No hay unidades suficientes")
    return product
