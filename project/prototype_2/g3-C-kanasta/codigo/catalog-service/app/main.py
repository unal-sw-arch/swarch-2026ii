from typing import Optional
from urllib.parse import unquote_plus

import psycopg
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from app.database import get_connection

app = FastAPI(title="Kanasta Catalog Service")

UNITS = ["unidad", "kg", "litro"]

PRODUCT_QUERY = """
    SELECT p.id, p.name, p.description, p.price, p.unit, p.stock, p.image,
           p.category_id, c.slug AS category, c.name AS category_name,
           COALESCE(ROUND(AVG(r.rating), 1), 0) AS rating, COUNT(r.id) AS reviews
    FROM products p
    JOIN categories c ON c.id = p.category_id
    LEFT JOIN reviews r ON r.product_id = p.id
"""
PRODUCT_GROUP = " GROUP BY p.id, c.slug, c.name, c.position"


class StoreIn(BaseModel):
    name: str = Field(min_length=1)
    slogan: str = ""
    address: str = ""
    phone: str = ""
    email: str = ""
    delivery_area: str = ""
    opening_hours: str = ""


class CategoryIn(BaseModel):
    name: str = Field(min_length=1)
    slug: str = Field(min_length=1)
    position: int = 50


class ProductIn(BaseModel):
    name: str = Field(min_length=1)
    description: str = ""
    price: int = Field(ge=0)
    unit: str = "unidad"
    stock: float = Field(ge=0)
    image: str = ""
    category_id: int


class StockRequest(BaseModel):
    quantity: float


class ReviewIn(BaseModel):
    rating: int = Field(ge=1, le=5)
    comment: str = ""


def find_product(conn, product_id):
    product = conn.execute(PRODUCT_QUERY + " WHERE p.id = %s" + PRODUCT_GROUP, [product_id]).fetchone()
    if product is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return product


def check_product(body: ProductIn):
    if body.unit not in UNITS:
        raise HTTPException(status_code=400, detail="La unidad debe ser unidad, kg o litro")


@app.get("/health")
def health():
    return {"status": "ok"}


# ---------- store settings ----------

@app.get("/api/catalog/store")
def get_store():
    with get_connection() as conn:
        return conn.execute("SELECT * FROM store WHERE id = 1").fetchone()


@app.put("/api/catalog/store")
def update_store(body: StoreIn):
    with get_connection() as conn:
        return conn.execute(
            """UPDATE store SET name = %s, slogan = %s, address = %s, phone = %s, email = %s,
               delivery_area = %s, opening_hours = %s WHERE id = 1 RETURNING *""",
            [body.name, body.slogan, body.address, body.phone, body.email, body.delivery_area, body.opening_hours],
        ).fetchone()


# ---------- categories ----------

@app.get("/api/catalog/categories")
def list_categories():
    with get_connection() as conn:
        return conn.execute("SELECT id, name, slug, position FROM categories ORDER BY position, name").fetchall()


@app.post("/api/catalog/categories", status_code=201)
def create_category(body: CategoryIn):
    try:
        with get_connection() as conn:
            return conn.execute(
                "INSERT INTO categories (name, slug, position) VALUES (%s, %s, %s) RETURNING *",
                [body.name, body.slug.lower(), body.position],
            ).fetchone()
    except psycopg.errors.UniqueViolation:
        raise HTTPException(status_code=409, detail="Ya existe una categoría con ese identificador")


@app.delete("/api/catalog/categories/{category_id}")
def delete_category(category_id: int):
    try:
        with get_connection() as conn:
            conn.execute("DELETE FROM categories WHERE id = %s", [category_id])
    except psycopg.errors.ForeignKeyViolation:
        raise HTTPException(status_code=409, detail="La categoría tiene productos; muévelos o elimínalos primero")
    return {"deleted": category_id}


# ---------- products ----------

@app.get("/api/catalog/products")
def list_products(search: str = "", category: str = ""):
    query = PRODUCT_QUERY + " WHERE 1 = 1"
    params = []
    if search:
        query += " AND (p.name ILIKE %s OR p.description ILIKE %s)"
        params += [f"%{search}%", f"%{search}%"]
    if category:
        query += " AND c.slug = %s"
        params.append(category)
    query += PRODUCT_GROUP + " ORDER BY c.position, p.name"

    with get_connection() as conn:
        return conn.execute(query, params).fetchall()


@app.get("/api/catalog/products/{product_id}")
def get_product(product_id: int):
    with get_connection() as conn:
        return find_product(conn, product_id)


@app.post("/api/catalog/products", status_code=201)
def create_product(body: ProductIn):
    check_product(body)
    try:
        with get_connection() as conn:
            row = conn.execute(
                """INSERT INTO products (name, description, price, unit, stock, image, category_id)
                   VALUES (%s, %s, %s, %s, %s, %s, %s) RETURNING id""",
                [body.name, body.description, body.price, body.unit, body.stock, body.image, body.category_id],
            ).fetchone()
            return find_product(conn, row["id"])
    except psycopg.errors.ForeignKeyViolation:
        raise HTTPException(status_code=400, detail="La categoría no existe")


@app.put("/api/catalog/products/{product_id}")
def update_product(product_id: int, body: ProductIn):
    check_product(body)
    try:
        with get_connection() as conn:
            row = conn.execute(
                """UPDATE products SET name = %s, description = %s, price = %s, unit = %s, stock = %s,
                   image = %s, category_id = %s WHERE id = %s RETURNING id""",
                [body.name, body.description, body.price, body.unit, body.stock, body.image, body.category_id, product_id],
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail="Producto no encontrado")
            return find_product(conn, product_id)
    except psycopg.errors.ForeignKeyViolation:
        raise HTTPException(status_code=400, detail="La categoría no existe")


@app.delete("/api/catalog/products/{product_id}")
def delete_product(product_id: int):
    with get_connection() as conn:
        row = conn.execute("DELETE FROM products WHERE id = %s RETURNING id", [product_id]).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return {"deleted": product_id}


# ---------- stock (used by the Orders Service) ----------

@app.post("/api/catalog/products/{product_id}/reserve")
def reserve_stock(product_id: int, body: StockRequest):
    if body.quantity <= 0:
        raise HTTPException(status_code=400, detail="La cantidad debe ser mayor que cero")

    with get_connection() as conn:
        product = conn.execute(
            "UPDATE products SET stock = stock - %s WHERE id = %s AND stock >= %s RETURNING id, name, stock",
            [body.quantity, product_id, body.quantity],
        ).fetchone()
    if product is None:
        raise HTTPException(status_code=409, detail="No hay cantidad suficiente")
    return product


@app.post("/api/catalog/products/{product_id}/release")
def release_stock(product_id: int, body: StockRequest):
    with get_connection() as conn:
        product = conn.execute(
            "UPDATE products SET stock = stock + %s WHERE id = %s RETURNING id, name, stock",
            [body.quantity, product_id],
        ).fetchone()
    if product is None:
        raise HTTPException(status_code=404, detail="Producto no encontrado")
    return product


# ---------- reviews ----------

@app.get("/api/catalog/products/{product_id}/reviews")
def list_reviews(product_id: int):
    with get_connection() as conn:
        return conn.execute(
            "SELECT id, user_id, user_name, rating, comment, created_at FROM reviews WHERE product_id = %s ORDER BY created_at DESC",
            [product_id],
        ).fetchall()


# The API Gateway adds the X-User-* headers after checking the session (the name comes URL-encoded)
@app.post("/api/catalog/products/{product_id}/reviews", status_code=201)
def save_review(
    product_id: int,
    body: ReviewIn,
    x_user_id: Optional[int] = Header(default=None),
    x_user_name: Optional[str] = Header(default=None),
):
    if x_user_id is None:
        raise HTTPException(status_code=401, detail="Debes iniciar sesión para opinar")

    with get_connection() as conn:
        find_product(conn, product_id)
        return conn.execute(
            """INSERT INTO reviews (product_id, user_id, user_name, rating, comment)
               VALUES (%s, %s, %s, %s, %s)
               ON CONFLICT (product_id, user_id)
               DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, created_at = NOW()
               RETURNING id, user_id, user_name, rating, comment, created_at""",
            [product_id, x_user_id, unquote_plus(x_user_name or "Cliente"), body.rating, body.comment.strip()],
        ).fetchone()
