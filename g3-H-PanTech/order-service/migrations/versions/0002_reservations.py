"""Reservas

Revision ID: 0002
Revises: 0001
"""
import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

S = "orders"


def upgrade() -> None:
    op.create_table(
        "reservations",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("customer_id", sa.String(64), nullable=False),
        sa.Column("reserved_date", sa.Date(), nullable=False),
        sa.Column("reserved_time", sa.Time(), nullable=False),
        sa.Column("people", sa.Integer(), nullable=False),
        sa.Column("notes", sa.Text()),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("cancel_reason", sa.String(200)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "status IN ('pendiente', 'confirmada', 'cancelada')", name="ck_reservations_status"
        ),
        sa.CheckConstraint("people > 0", name="ck_reservations_people"),
        schema=S,
    )
    op.create_index("ix_reservations_customer_id", "reservations", ["customer_id"], schema=S)
    op.create_index("ix_reservations_reserved_date", "reservations", ["reserved_date"], schema=S)


def downgrade() -> None:
    op.drop_table("reservations", schema=S)
