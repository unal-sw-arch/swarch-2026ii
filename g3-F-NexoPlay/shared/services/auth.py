"""Verify the caller's JWT, including on direct API access that bypasses the gateway."""

from dataclasses import dataclass
import os
from typing import Annotated
from uuid import UUID

import jwt
from fastapi import Header, HTTPException

NAME_MAX_LENGTH = 100


@dataclass(frozen=True)
class Identity:
    user_id: UUID
    # Display name from the token; None for tokens issued before it was added.
    name: str | None


def _clean_name(value) -> str | None:
    if not isinstance(value, str):
        return None
    value = " ".join(value.split())[:NAME_MAX_LENGTH]
    return value or None


def _decode_identity(authorization: str | None) -> Identity:
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(401, "Token required.")
    secret = os.getenv("JWT_SECRET")
    if not secret:
        raise HTTPException(503, "Authentication is temporarily unavailable.")
    try:
        payload = jwt.decode(
            token, secret, algorithms=["HS256"], options={"require": ["sub", "exp"]},
        )
        return Identity(UUID(payload["sub"]), _clean_name(payload.get("name")))
    except (jwt.PyJWTError, ValueError, TypeError, AttributeError) as error:
        raise HTTPException(401, "Invalid or expired token.") from error


def _decode_user(authorization: str | None) -> UUID:
    return _decode_identity(authorization).user_id


def authenticated_user(
    authorization: Annotated[str | None, Header()] = None,
) -> UUID:
    return _decode_user(authorization)


def authenticated_identity(
    authorization: Annotated[str | None, Header()] = None,
) -> Identity:
    return _decode_identity(authorization)


def optional_user(
    authorization: Annotated[str | None, Header()] = None,
) -> UUID | None:
    """Anonymous callers get None; a token that is present must still be valid."""
    if not authorization:
        return None
    return _decode_user(authorization)
