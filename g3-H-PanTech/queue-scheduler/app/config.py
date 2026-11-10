"""Configuración del Queue Scheduler leída desde variables de entorno."""
import os

# SQL de solo lectura hacia PostgreSQL (tiempos base de preparación).
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://panaderia:panaderia@postgres:5432/panaderia",
)

# Redis: cola priorizada y estados temporales de las comandas.
REDIS_URL = os.getenv("REDIS_URL", "redis://redis:6379/0")

# TTL (segundos) con el que se conservan las comandas ya entregadas en Redis.
DELIVERED_TTL_SECONDS = int(os.getenv("DELIVERED_TTL_SECONDS", "3600"))
