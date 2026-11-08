from celery_app import app
from deps import build_deps
import handlers
from tasks.base import RETRY_OPTIONS, EventTask


@app.task(base=EventTask, bind=True, name="worker.generate_receipt", event_name="order.paid", **RETRY_OPTIONS)
def generate_receipt_task(self, event: dict) -> str:
    return handlers.generate_receipt(event, build_deps())
