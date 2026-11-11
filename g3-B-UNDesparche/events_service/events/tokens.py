from django.core import signing
from django.conf import settings


def make_unsubscribe_token(subscription) -> str:
    return signing.dumps({"sid": subscription.id}, salt=settings.UNSUBSCRIBE_TOKEN_SALT)


def read_unsubscribe_token(token: str) -> dict:
    return signing.loads(token, salt=settings.UNSUBSCRIBE_TOKEN_SALT)
