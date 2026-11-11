from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape
from jinja2.sandbox import SandboxedEnvironment

from .catalog import TemplateSpec
from .schemas import NotificationMessage, Recipient

TZ = ZoneInfo("America/Bogota")
_DAYS = ["lun", "mar", "mié", "jue", "vie", "sáb", "dom"]
_MONTHS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]


def datetime_es(value) -> str:
    """ISO-8601 (o datetime) -> 'mié 5 nov 2026, 3:00 p. m.' en hora de Bogotá."""
    dt = value if isinstance(value, datetime) else datetime.fromisoformat(str(value))
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=ZoneInfo("UTC"))
    dt = dt.astimezone(TZ)
    hour12 = dt.hour % 12 or 12
    suffix = "a. m." if dt.hour < 12 else "p. m."
    return f"{_DAYS[dt.weekday()]} {dt.day} {_MONTHS[dt.month - 1]} {dt.year}, {hour12}:{dt.minute:02d} {suffix}"


_env = Environment(
    loader=FileSystemLoader(Path(__file__).parent / "templates"),
    autoescape=select_autoescape(["html"]),
    undefined=StrictUndefined,
)
_env.filters["datetime_es"] = datetime_es

_subject_env = SandboxedEnvironment(undefined=StrictUndefined)


def render_email(spec: TemplateSpec, message: NotificationMessage, recipient: Recipient,
                 frontend_url: str) -> tuple[str, str]:
    context = {
        **message.data,
        "recipient": recipient,
        "source": message.source,
        "frontend_url": frontend_url,
    }
    subject = _subject_env.from_string(spec.subject).render(**context).strip()
    html = _env.get_template(spec.template).render(**context)
    return subject, html
