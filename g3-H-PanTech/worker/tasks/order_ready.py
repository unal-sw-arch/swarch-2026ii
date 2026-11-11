from celery_app import app
from deps import build_deps
import handlers
from tasks.base import RETRY_OPTIONS, EventTask


@app.task(base=EventTask, bind=True, name="worker.process_order_ready", event_name="order.ready", **RETRY_OPTIONS)
def process_order_ready(self, event: dict) -> str:
    return handlers.handle_order_ready(event, build_deps())
