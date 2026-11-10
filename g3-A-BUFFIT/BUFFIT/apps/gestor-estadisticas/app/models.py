from datetime import datetime

from app.database import db


class PedidoSubsidio(db.Model):
    """Una fila por pedido. Se crea/actualiza al procesar los eventos del broker."""
    __tablename__ = "pedido_subsidio"

    order_id = db.Column(db.Integer, primary_key=True, autoincrement=False)  # id en Gestor de Pedidos
    user_id = db.Column(db.String(100), nullable=False, index=True)
    user_type = db.Column(db.String(50), nullable=False)
    dining_hall = db.Column(db.String(100), nullable=False, index=True)
    time_slot = db.Column(db.String(50))
    precio_referencia = db.Column(db.Float, nullable=False)
    valor_pagado = db.Column(db.Float, nullable=False)
    subsidio = db.Column(db.Float, nullable=False)
    estado = db.Column(db.String(30), nullable=False, index=True)  # CONFIRMED | DELIVERED | CANCELLED
    fecha = db.Column(db.Date, nullable=False, index=True)          # dia del pedido (hora de Bogota)
    creado_en = db.Column(db.DateTime, default=datetime.utcnow)
    actualizado_en = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class EventoProcesado(db.Model):
    """Eventos ya aplicados (idempotencia ante entregas repetidas del broker)."""
    __tablename__ = "evento_procesado"

    event_id = db.Column(db.String(64), primary_key=True)
    tipo = db.Column(db.String(40), nullable=False)
    order_id = db.Column(db.Integer, nullable=False)
    procesado_en = db.Column(db.DateTime, default=datetime.utcnow)
