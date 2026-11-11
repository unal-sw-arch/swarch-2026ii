import os

import psycopg
from psycopg.rows import dict_row

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://kanasta:kanasta@localhost:5432/catalog")


def get_connection():
    return psycopg.connect(DATABASE_URL, row_factory=dict_row)
