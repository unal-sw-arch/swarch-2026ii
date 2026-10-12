import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    PORT = int(os.getenv("PORT", 8003))
    GRPC_PORT = int(os.getenv("GRPC_PORT", 50051))
    MONGO_URI = os.getenv(
        "MONGO_URI",
        "mongodb://mongo_buffit:password_buffit@localhost:27017/?authSource=admin",
    )
    MONGO_DB = os.getenv("MONGO_DB", "bd_inventario")
    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "clave_secreta_super_segura_buffit")
    JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
    ENFORCE_ROLES = os.getenv("ENFORCE_ROLES", "true").lower() == "true"
    WRITE_ROLES = ("Comedor", "Administrador")
