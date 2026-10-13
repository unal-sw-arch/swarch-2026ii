"""Verify identity for destructive requests, including direct API access."""

import os
from typing import Annotated
from uuid import UUID

import jwt
from fastapi import Header, HTTPException


def authenticated_user(
    authorization: Annotated[str | None, Header()] = None,
) -> UUID:
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
        return UUID(payload["sub"])
    except (jwt.PyJWTError, ValueError, TypeError, AttributeError) as error:
        raise HTTPException(401, "Invalid or expired token.") from error
