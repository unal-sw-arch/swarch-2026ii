from celery_app import app
from deps import build_deps
import handlers
from tasks.base import RETRY_OPTIONS, EventTask


@app.task(base=EventTask, bind=True, name="worker.process_reservation_created",
          event_name="reservation.created", **RETRY_OPTIONS)
def process_reservation_created(self, event: dict) -> str:
    return handlers.handle_reservation_event("reservation.created", event, build_deps())


@app.task(base=EventTask, bind=True, name="worker.process_reservation_confirmed",
          event_name="reservation.confirmed", **RETRY_OPTIONS)
def process_reservation_confirmed(self, event: dict) -> str:
    return handlers.handle_reservation_event("reservation.confirmed", event, build_deps())


@app.task(base=EventTask, bind=True, name="worker.process_reservation_cancelled",
          event_name="reservation.cancelled", **RETRY_OPTIONS)
def process_reservation_cancelled(self, event: dict) -> str:
    return handlers.handle_reservation_event("reservation.cancelled", event, build_deps())
