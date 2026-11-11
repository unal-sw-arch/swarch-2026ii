"""
stats-service: estadísticas de inscripción de Pilosia.

Escucha los eventos de inscripción que publica courses-service en RabbitMQ,
los guarda en su propia base de datos (PostgreSQL) y expone un resumen por REST
que usa el panel administrativo.
"""
import json
import logging
import os
import threading
import time

import pika
import psycopg
from fastapi import FastAPI
from psycopg.rows import dict_row

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://stats:stats@localhost:5432/stats_db")
RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
EXCHANGE = "pilosia.events"
QUEUE = "pilosia.stats"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("stats-service")

app = FastAPI(title="Pilosia - stats-service", version="1.0")


def get_connection():
    return psycopg.connect(DATABASE_URL, row_factory=dict_row)


def save_event(event):
    """Guarda el evento y actualiza el resumen de la asignatura."""
    if event.get("type") == "EnrollmentCreated":
        column = "enrolled"
    elif event.get("type") == "EnrollmentCancelled":
        column = "cancelled"
    else:
        return

    with get_connection() as conn:
        conn.execute(
            """INSERT INTO enrollment_events (event_type, student_code, group_id, course_code)
               VALUES (%s, %s, %s, %s)""",
            (event["type"], event["studentCode"], event["groupId"], event["courseCode"]),
        )
        # el nombre de la columna sale del if de arriba, no del mensaje
        conn.execute(
            f"""INSERT INTO course_stats (course_code, course_name, {column}, last_event_at)
                VALUES (%s, %s, 1, NOW())
                ON CONFLICT (course_code) DO UPDATE
                SET {column} = course_stats.{column} + 1, last_event_at = NOW()""",
            (event["courseCode"], event["courseName"]),
        )


def on_message(channel, method, properties, body):
    try:
        save_event(json.loads(body))
        logger.info("Evento guardado: %s", body)
    except Exception:
        logger.exception("No se pudo procesar el evento %s", body)
    # se confirma igual para que un mensaje dañado no se quede repitiendo
    channel.basic_ack(delivery_tag=method.delivery_tag)


def consume_forever():
    """Escucha la cola en un hilo aparte. Si RabbitMQ se cae, vuelve a intentar."""
    while True:
        try:
            connection = pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))
            channel = connection.channel()
            channel.exchange_declare(exchange=EXCHANGE, exchange_type="fanout", durable=True)
            channel.queue_declare(queue=QUEUE, durable=True)
            channel.queue_bind(queue=QUEUE, exchange=EXCHANGE)
            channel.basic_consume(queue=QUEUE, on_message_callback=on_message)
            logger.info("Escuchando eventos en la cola %s", QUEUE)
            channel.start_consuming()
        except Exception:
            logger.exception("Conexión con RabbitMQ perdida, reintentando en 5 segundos")
            time.sleep(5)


@app.on_event("startup")
def start_consumer():
    threading.Thread(target=consume_forever, daemon=True).start()


@app.get("/stats/health")
def health():
    return {"status": "ok"}


@app.get("/stats/courses")
def course_stats():
    """Resumen de inscripciones y cancelaciones por asignatura."""
    with get_connection() as conn:
        rows = conn.execute(
            """SELECT course_code, course_name, enrolled, cancelled,
                      to_char(last_event_at, 'YYYY-MM-DD HH24:MI:SS') AS last_event_at
               FROM course_stats ORDER BY enrolled DESC, course_name"""
        ).fetchall()
    return rows


@app.get("/stats/events")
def recent_events(limit: int = 20):
    """Últimos eventos recibidos."""
    with get_connection() as conn:
        rows = conn.execute(
            """SELECT event_type, student_code, group_id, course_code,
                      to_char(received_at, 'YYYY-MM-DD HH24:MI:SS') AS received_at
               FROM enrollment_events ORDER BY id DESC LIMIT %s""",
            (limit,),
        ).fetchall()
    return rows
