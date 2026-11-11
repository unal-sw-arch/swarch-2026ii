# app/models.py
from datetime import datetime
from app.database import db

# Precios fijos según el tipo de usuario (puedes ajustar los montos si es necesario)
PRICE_MAP = {
    "APOYO_ALIMENTARIO": 0.0,  # Subsidio completo: paga 0
    "ESTUDIANTE": 8400.0,       # Con carnet universitario
    "GENERAL": 16500.0         # Sin carnet: tarifa plena (también es el precio de referencia del subsidio)
}

STATUS_LABELS = {
    "CONFIRMED": "Sin entregar",
    "DELIVERED": "Entregada",
    "CANCELLED": "Cancelada",
}

class Order(db.Model):
    __tablename__ = "orders"

    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    user_id = db.Column(db.String(100), nullable=False)
    user_type = db.Column(db.String(50), nullable=False, default="ESTUDIANTE") # APOYO_ALIMENTARIO, ESTUDIANTE, GENERAL
    dining_hall = db.Column(db.String(100), nullable=False)       # Comedor seleccionado
    pickup_time_slot = db.Column(db.String(50), nullable=False)   # Franja horaria (15 min)
    total_price = db.Column(db.Float, nullable=False)             # Tarifa fija calculada según user_type
    status = db.Column(db.String(30), default="CONFIRMED")
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

    items = db.relationship("OrderItem", backref="order", cascade="all, delete-orphan", lazy=True)

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "user_type": self.user_type,
            "dining_hall": self.dining_hall,
            "pickup_time_slot": self.pickup_time_slot,
            "total_price": self.total_price,
            "status": self.status,
            "status_label": STATUS_LABELS.get(self.status, self.status),
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "items": [item.to_dict() for item in self.items]
        }

class OrderItem(db.Model):
    __tablename__ = "order_items"

    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.id"), nullable=False)
    product_name = db.Column(db.String(100), nullable=False)
    category = db.Column(db.String(50), nullable=False)           # Proteína, Carbohidrato, Verdura, etc.
    quantity = db.Column(db.Integer, default=1)

    def to_dict(self):
        return {
            "id": self.id,
            "product_name": self.product_name,
            "category": self.category,
            "quantity": self.quantity
        }