"""Modelos SQLAlchemy del Order Manager (tablas en PostgreSQL)."""
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Integer,
    Numeric,
    String,
    func,
)
from sqlalchemy.orm import relationship

from .database import Base


class Product(Base):
    __tablename__ = "products"

    id = Column(Integer, primary_key=True)
    name = Column(String(120), nullable=False)
    category = Column(String(20), nullable=False)  # vitrina | preparado
    prep_minutes = Column(Integer, nullable=False, default=1)
    price = Column(Numeric(10, 2), nullable=False, default=0)
    active = Column(Boolean, nullable=False, default=True)


class Order(Base):
    __tablename__ = "orders"

    id = Column(Integer, primary_key=True)
    type = Column(String(20), nullable=False)  # para_llevar | en_mesa
    table_number = Column(Integer, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    items = relationship(
        "OrderItem",
        back_populates="order",
        cascade="all, delete-orphan",
        lazy="selectin",
    )


class OrderItem(Base):
    __tablename__ = "order_items"

    id = Column(Integer, primary_key=True)
    order_id = Column(Integer, ForeignKey("orders.id", ondelete="CASCADE"), nullable=False)
    product_id = Column(Integer, ForeignKey("products.id"), nullable=False)
    quantity = Column(Integer, nullable=False, default=1)

    order = relationship("Order", back_populates="items")
    product = relationship("Product", lazy="joined")

    @property
    def name(self):
        """Nombre del producto (se expone en la API)."""
        return self.product.name if self.product else None


class Reservation(Base):
    __tablename__ = "reservations"

    id = Column(Integer, primary_key=True)
    customer_name = Column(String(120), nullable=False)
    party_size = Column(Integer, nullable=False, default=1)
    reserved_at = Column(DateTime(timezone=True), nullable=False)
    notes = Column(String(500), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
