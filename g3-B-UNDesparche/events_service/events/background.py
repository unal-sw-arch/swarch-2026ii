"""Hilo en segundo plano de Events: avanza periódicamente el estado de los eventos.

Solo es el reloj; la lógica vive en services.py para poder probarla sin hilo.
"""

import logging
import threading
import time

from django.db import connection

from . import services

logger = logging.getLogger(__name__)

# Las fechas de los eventos se manejan al minuto, así que 30 s es margen de sobra
CHECK_INTERVAL_SECONDS = 30

_lock = threading.Lock()
_thread: threading.Thread | None = None


def start() -> None:
    """Arranca el hilo. Si ya hay uno vivo en este proceso, no hace nada."""
    global _thread
    with _lock:
        if _thread is not None and _thread.is_alive():
            return
        # daemon=True: el hilo muere junto con el proceso y no bloquea el apagado
        _thread = threading.Thread(
            target=_run, name="event-state-updater", daemon=True
        )
        _thread.start()
        logger.info(
            "Hilo de estados de eventos iniciado (cada %d s)", CHECK_INTERVAL_SECONDS
        )


def _run() -> None:
    while True:
        # Se espera primero: así no corre durante el arranque ni mientras
        # se están aplicando migraciones
        time.sleep(CHECK_INTERVAL_SECONDS)
        try:
            result = services.advance_event_states()
            if result["started"] or result["finished"]:
                logger.info(
                    "Eventos iniciados: %d, finalizados: %d",
                    result["started"],
                    result["finished"],
                )
        except Exception:
            # Un fallo puntual no debe matar el hilo: se registra y se reintenta
            logger.exception("Falló la actualización de estados de eventos")
        finally:
            # Cada hilo tiene su propia conexión a la BD; cerrarla evita
            # reutilizar una que el servidor ya haya descartado por inactividad
            connection.close()
