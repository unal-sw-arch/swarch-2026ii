from django.core import signing
from django.conf import settings

SALT = getattr(settings, "UNSUBSCRIBE_TOKEN_SALT", "undesparche-unsub-salt")


def make_unsubscribe_token(subscription) -> str:
    return signing.dumps({"sid": subscription.id}, salt=SALT)


def read_unsubscribe_token(token: str) -> dict:
    return signing.loads(token, salt=SALT)
