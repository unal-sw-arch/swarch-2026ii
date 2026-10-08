import os

import psycopg
from psycopg.rows import dict_row

DATABASE_URL = os.getenv(
    "DATABASE_URL", "postgresql://pilosia:pilosia@localhost:5432/courses_db"
)


def get_connection():
    """Abre una conexión a PostgreSQL. Las filas se devuelven como diccionarios."""
    return psycopg.connect(DATABASE_URL, row_factory=dict_row)
