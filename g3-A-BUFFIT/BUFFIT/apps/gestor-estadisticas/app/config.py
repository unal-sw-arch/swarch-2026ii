import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    PORT = int(os.getenv("PORT", 8005))
    SQLALCHEMY_DATABASE_URI = os.getenv("DATABASE_URL", "sqlite:///stats.db")
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # Broker de mensajes
    RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
    EVENTS_EXCHANGE = os.getenv("EVENTS_EXCHANGE", "pedidos")
    EVENTS_QUEUE = os.getenv("EVENTS_QUEUE", "estadisticas.pedidos")
    EVENTS_DLQ = os.getenv("EVENTS_DLQ", "estadisticas.pedidos.dlq")
    CONSUMER_ENABLED = os.getenv("CONSUMER_ENABLED", "true").lower() == "true"

    # Precio de referencia (tarifa plena sin carnet). subsidio = referencia - valor pagado
    REFERENCE_PRICE = float(os.getenv("REFERENCE_PRICE", 16500))

    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "clave_secreta_super_segura_buffit")
    JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
    ALLOWED_ROLES = ("Comedor", "Administrador")
