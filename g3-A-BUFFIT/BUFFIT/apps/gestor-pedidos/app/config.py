import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    PORT = int(os.getenv("PORT", 8002))
    SQLALCHEMY_DATABASE_URI = os.getenv("DATABASE_URL", "sqlite:///orders.db")
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # gRPC hacia el Gestor de Inventario (docker-compose define INVENTORY_GRPC_TARGET)
    INVENTORY_GRPC_HOST = os.getenv("INVENTORY_GRPC_TARGET", "localhost:50051")
    # HTTP hacia el Gestor de Inventario (se usa para devolver stock al cancelar)
    INVENTORY_HTTP_URL = os.getenv("INVENTORY_HTTP_URL", "http://localhost:8003")

    # JWT emitido por el AuthService (mismo secreto compartido)
    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "clave_secreta_super_segura_buffit")
    JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")

    # Broker de mensajes (RabbitMQ): eventos hacia el Gestor de Estadisticas
    RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
    EVENTS_EXCHANGE = os.getenv("EVENTS_EXCHANGE", "pedidos")
    STATS_QUEUE = os.getenv("EVENTS_QUEUE", "estadisticas.pedidos")
    STATS_DLQ = os.getenv("EVENTS_DLQ", "estadisticas.pedidos.dlq")
    EVENTS_ENABLED = os.getenv("EVENTS_ENABLED", "true").lower() == "true"

    STAFF_ROLES = ("Comedor", "Administrador")
    CANCEL_MIN_MINUTES = int(os.getenv("CANCEL_MIN_MINUTES", 30))
