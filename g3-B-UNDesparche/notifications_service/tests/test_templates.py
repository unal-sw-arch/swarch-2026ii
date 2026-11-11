import pytest

from app.catalog import CATALOG, InvalidMessage, get_spec, validate_data
from app.rendering import datetime_es, render_email
from app.schemas import NotificationMessage

SAMPLE = {
    "event_name": "Torneo de ajedrez", "place": "Edificio 401", "previous_place": "Plaza Che",
    "datetime_start": "2026-11-05T20:00:00Z", "datetime_end": "2026-11-05T23:00:00Z",
    "previous_datetime_start": "2026-11-04T20:00:00Z",
    "implement_name": "Mesa de ping pong", "expires_at": "2026-11-05T15:10:00Z",
    "borrowed_at": "2026-11-05T15:00:00Z", "returned_at": "2026-11-05T16:00:00Z",
}


def msg(type_, data=SAMPLE, unsub=None):
    return NotificationMessage.model_validate({
        "message_id": "11111111-2222-3333-4444-555555555555", "type": type_,
        "source": CATALOG[type_].source, "occurred_at": "2026-11-02T10:00:00Z",
        "recipients": [{"email": "ana@unal.edu.co", "name": "Ana", "unsubscribe_url": unsub}],
        "data": data})


@pytest.mark.parametrize("type_", list(CATALOG))
def test_every_type_renders(type_):
    m = msg(type_, unsub="http://x/unsubscribe?token=abc")
    spec = get_spec(type_)
    validate_data(spec, m.data)
    subject, html = render_email(spec, m, m.recipients[0], "http://localhost:5172")
    assert subject and "Hola Ana" in html and "unsubscribe?token=abc" in html


def test_timezone_is_bogota():
    assert datetime_es("2026-11-05T20:00:00Z") == "jue 5 nov 2026, 3:00 p. m."


def test_missing_field_is_invalid():
    with pytest.raises(InvalidMessage):
        validate_data(get_spec("event.cancelled"), {"event_name": "x"})


def test_unknown_type_is_invalid():
    with pytest.raises(InvalidMessage):
        get_spec("nope")


def test_html_is_escaped():
    m = msg("event.finished", {"event_name": "<script>alert(1)</script>"})
    _, html = render_email(get_spec("event.finished"), m, m.recipients[0], "")
    assert "<script>" not in html
