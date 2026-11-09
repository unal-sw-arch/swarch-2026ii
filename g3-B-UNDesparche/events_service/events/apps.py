import os

from django.apps import AppConfig


class EventsConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "events"

    def ready(self):
        # Opt-in por variable de entorno: migrate, shell y los tests también
        # ejecutan ready() y no deben lanzar el hilo. Solo se define en docker-compose.
        if os.environ.get("ENABLE_BACKGROUND_TASKS") == "1":
            from . import background

            background.start()
