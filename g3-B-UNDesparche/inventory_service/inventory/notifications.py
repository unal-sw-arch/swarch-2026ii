"""Construye y encola las notificaciones de Inventory. Pensado para llamarse desde views.py / tasks.py."""

from core.notifications_publisher import publish_notification


def _recipient(email: str) -> dict:
    return {"email": email, "name": None}


def _iso(value):
    return value.isoformat() if value else None


def notify_reserved(reserve) -> None:
    publish_notification(
        "implement.reserved",
        [_recipient(reserve.user_email)],
        {
            "implement_name": reserve.implement.name,
            "expires_at": _iso(reserve.datetime_expiration),
        },
    )


def notify_reserve_expired(reserve) -> None:
    publish_notification(
        "implement.reserve_expired",
        [_recipient(reserve.user_email)],
        {"implement_name": reserve.implement.name},
    )


def notify_borrowed(borrowing) -> None:
    publish_notification(
        "implement.borrowed",
        [_recipient(borrowing.user_email)],
        {
            "implement_name": borrowing.implement.name,
            "borrowed_at": _iso(borrowing.datetime_borrowed),
        },
    )


def notify_returned(borrowing) -> None:
    publish_notification(
        "implement.returned",
        [_recipient(borrowing.user_email)],
        {
            "implement_name": borrowing.implement.name,
            "returned_at": _iso(borrowing.datetime_return),
        },
    )
