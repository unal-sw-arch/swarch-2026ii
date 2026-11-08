"""Outbox de eventos

Revision ID: 0003
Revises: 0002
"""
import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None

S = "orders"


def upgrade() -> None:
    op.create_table(
        "outbox",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("event_type", sa.String(60), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True)),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("next_attempt_at", sa.DateTime(timezone=True)),
        sa.Column("last_error", sa.Text()),
        schema=S,
    )
    op.create_index("ix_outbox_pending", "outbox", ["published_at", "next_attempt_at"], schema=S)


def downgrade() -> None:
    op.drop_table("outbox", schema=S)
