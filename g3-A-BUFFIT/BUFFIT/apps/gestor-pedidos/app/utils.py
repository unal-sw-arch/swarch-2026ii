import re
from datetime import datetime, timedelta, timezone

# Colombia no tiene horario de verano: UTC-5 fijo
BOGOTA = timezone(timedelta(hours=-5))

_SLOT_RE = re.compile(r"(\d{1,2}):(\d{2})\s*(AM|PM)", re.IGNORECASE)


def slot_start_minutes(slot: str):
    """'11:30 AM - 11:45 AM' / '1:15 PM - 1:30 PM' -> minutos desde medianoche, o None."""
    m = _SLOT_RE.search(slot or "")
    if not m:
        return None
    hour, minute, ampm = int(m.group(1)), int(m.group(2)), m.group(3).upper()
    hour = hour % 12 + (12 if ampm == "PM" else 0)
    return hour * 60 + minute


def now_bogota() -> datetime:
    return datetime.now(BOGOTA)


def to_bogota(naive_utc: datetime) -> datetime:
    return naive_utc.replace(tzinfo=timezone.utc).astimezone(BOGOTA)


def day_bounds_utc(day):
    """Inicio y fin (UTC naive) de un día calendario de Bogotá."""
    start = datetime(day.year, day.month, day.day, tzinfo=BOGOTA)
    end = start + timedelta(days=1)
    return (start.astimezone(timezone.utc).replace(tzinfo=None),
            end.astimezone(timezone.utc).replace(tzinfo=None))
