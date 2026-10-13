"""Acceso a PostgreSQL desde el Queue Scheduler.

- Lectura: tiempos base de preparación de los productos.
- Escritura: historial de estados de las comandas (Requerimiento 9).
"""
import asyncio
import logging

import asyncpg

from . import config

logger = logging.getLogger("queue-scheduler.db")

# Historial durable de estados de las comandas. Idempotente: se puede ejecutar
# en cada arranque sin riesgo (la crea también el Order Manager si aplica).
SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS order_status_events (
    id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_order_status_events_order_id
    ON order_status_events (order_id);
"""


async def create_pool_with_retry(retries: int = 30, delay: float = 2.0) -> asyncpg.Pool:
    """Crea el pool de conexiones reintentando hasta que PostgreSQL esté listo."""
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            pool = await asyncpg.create_pool(
                dsn=config.DATABASE_URL, min_size=1, max_size=5, command_timeout=10
            )
            logger.info("Conectado a PostgreSQL (intento %s)", attempt)
            return pool
        except Exception as exc:  # noqa: BLE001 - queremos reintentar cualquier fallo
            last_error = exc
            logger.warning(
                "PostgreSQL no disponible (intento %s/%s): %s", attempt, retries, exc
            )
            await asyncio.sleep(delay)
    raise RuntimeError("No se pudo conectar a PostgreSQL") from last_error


async def ensure_schema(pool: asyncpg.Pool) -> None:
    """Crea la tabla del historial de estados si no existe."""
    await pool.execute(SCHEMA_SQL)


async def fetch_products(pool: asyncpg.Pool, product_ids: list):
    """Devuelve ``{id: {"name": str, "prep_minutes": int}}`` para los productos dados."""
    if not product_ids:
        return {}
    rows = await pool.fetch(
        "SELECT id, name, prep_minutes FROM products WHERE id = ANY($1::int[])",
        product_ids,
    )
    return {
        row["id"]: {"name": row["name"], "prep_minutes": row["prep_minutes"]}
        for row in rows
    }


async def record_status_event(pool: asyncpg.Pool, order_id: int, status: str) -> None:
    """Registra un cambio de estado de una comanda con su marca de tiempo."""
    await pool.execute(
        "INSERT INTO order_status_events (order_id, status) VALUES ($1, $2)",
        order_id,
        status,
    )


async def fetch_order_events(pool: asyncpg.Pool, order_id: int) -> list:
    """Historial de una comanda, en orden cronológico."""
    return await pool.fetch(
        """
        SELECT status, changed_at
        FROM order_status_events
        WHERE order_id = $1
        ORDER BY changed_at, id
        """,
        order_id,
    )


async def fetch_history(pool: asyncpg.Pool, limit: int = 50) -> list:
    """Resumen por comanda: inicio, hora de listo, entrega y estado actual."""
    return await pool.fetch(
        """
        SELECT order_id,
               min(changed_at) AS started_at,
               max(changed_at) FILTER (WHERE status = 'listo') AS ready_at,
               max(changed_at) FILTER (WHERE status = 'entregado') AS delivered_at,
               (array_agg(status ORDER BY changed_at DESC, id DESC))[1] AS last_status
        FROM order_status_events
        GROUP BY order_id
        ORDER BY order_id DESC
        LIMIT $1
        """,
        limit,
    )


async def ping(pool: asyncpg.Pool) -> None:
    await pool.fetchval("SELECT 1")
