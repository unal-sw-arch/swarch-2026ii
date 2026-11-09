"""Hilo en segundo plano de Inventory: libera periódicamente las reservas vencidas.

Solo es el reloj; la lógica vive en services.py para poder probarla sin hilo.
"""

import logging
import threading
import time

from django.db import connection

from . import services

logger = logging.getLogger(__name__)

# Con un plazo de reserva de 10 minutos, 15 s de margen es más que suficiente
CHECK_INTERVAL_SECONDS = 15

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
            target=_run, name="reserve-expirer", daemon=True
        )
        _thread.start()
        logger.info(
            "Hilo de expiración de reservas iniciado (cada %d s)",
            CHECK_INTERVAL_SECONDS,
        )


def _run() -> None:
    while True:
        # Se espera primero: así no corre durante el arranque ni mientras
        # se están aplicando migraciones
        time.sleep(CHECK_INTERVAL_SECONDS)
        try:
            released = services.release_expired_reserves()
            if released:
                logger.info("Reservas vencidas liberadas: %d", released)
        except Exception:
            # Un fallo puntual no debe matar el hilo: se registra y se reintenta
            logger.exception("Falló la revisión de reservas vencidas")
        finally:
            # Cada hilo tiene su propia conexión a la BD; cerrarla evita
            # reutilizar una que el servidor ya haya descartado por inactividad
            connection.close()
