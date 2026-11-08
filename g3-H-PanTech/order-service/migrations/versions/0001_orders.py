"""Pedidos, ítems y pagos (esquema orders)

Revision ID: 0001
Revises:
"""
import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

S = "orders"


def upgrade() -> None:
    op.create_table(
        "orders",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("customer_id", sa.String(64)),
        sa.Column("created_by", sa.String(64), nullable=False),
        sa.Column("order_type", sa.String(20), nullable=False),
        sa.Column("table_number", sa.Integer()),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("total", sa.Numeric(12, 2), nullable=False),
        sa.Column("notes", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("order_type IN ('para_llevar', 'en_mesa')", name="ck_orders_order_type"),
        sa.CheckConstraint(
            "status IN ('pendiente', 'pagado', 'en_preparacion', 'listo', 'entregado', 'cancelado')",
            name="ck_orders_status",
        ),
        sa.CheckConstraint(
            "(order_type = 'en_mesa' AND table_number IS NOT NULL) "
            "OR (order_type = 'para_llevar' AND table_number IS NULL)",
            name="ck_orders_table_number",
        ),
        schema=S,
    )
    op.create_index("ix_orders_customer_id", "orders", ["customer_id"], schema=S)
    op.create_index("ix_orders_status", "orders", ["status"], schema=S)

    op.create_table(
        "order_items",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("order_id", sa.Uuid(), sa.ForeignKey("orders.orders.id", ondelete="CASCADE"), nullable=False),
        sa.Column("product_id", sa.String(64), nullable=False),
        sa.Column("product_name", sa.String(120), nullable=False),
        sa.Column("unit_price", sa.Numeric(12, 2), nullable=False),
        sa.Column("prep_minutes", sa.Integer(), nullable=False),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("line_total", sa.Numeric(12, 2), nullable=False),
        sa.CheckConstraint("quantity > 0", name="ck_order_items_quantity"),
        schema=S,
    )
    op.create_index("ix_order_items_order_id", "order_items", ["order_id"], schema=S)
    op.create_index("ix_order_items_product_id", "order_items", ["product_id"], schema=S)

    op.create_table(
        "payments",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("order_id", sa.Uuid(), sa.ForeignKey("orders.orders.id"), nullable=False, unique=True),
        sa.Column("amount", sa.Numeric(12, 2), nullable=False),
        sa.Column("method", sa.String(20), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("idempotency_key", sa.String(100)),
        sa.Column("paid_by", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        schema=S,
    )


def downgrade() -> None:
    op.drop_table("payments", schema=S)
    op.drop_table("order_items", schema=S)
    op.drop_table("orders", schema=S)
