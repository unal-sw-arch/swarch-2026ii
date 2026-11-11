"""Cliente HTTP de Kitchen Service: POST /queue/orders (idempotente por order_id)."""
import httpx

import config
from errors import InvalidEvent, TransientError

_ACCEPTED = (200, 201, 202, 409)  # 409 = ya estaba encolado


class KitchenClient:
    def __init__(self, base_url: str | None = None, service_token: str | None = None,
                 timeout: float | None = None, transport: httpx.BaseTransport | None = None):
        self._client = httpx.Client(
            base_url=base_url or config.KITCHEN_URL,
            timeout=timeout or config.HTTP_TIMEOUT,
            transport=transport,
        )
        self._token = service_token if service_token is not None else config.SERVICE_TOKEN

    def enqueue(self, order: dict, request_id: str | None = None) -> None:
        headers = {"X-Service-Token": self._token}
        if request_id:
            headers["X-Request-Id"] = request_id
        try:
            response = self._client.post("/queue/orders", json=order, headers=headers)
        except httpx.HTTPError as exc:
            raise TransientError(f"Kitchen Service no responde: {exc}") from exc
        if response.status_code in _ACCEPTED:
            return
        if response.status_code in (400, 422):
            raise InvalidEvent(f"Kitchen rechazó el pedido ({response.status_code}): {response.text[:300]}")
        raise TransientError(f"Kitchen respondió {response.status_code}: {response.text[:300]}")
