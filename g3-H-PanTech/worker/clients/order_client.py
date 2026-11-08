"""Cliente HTTP del Order Service para avanzar el estado de un pedido (/internal, X-Service-Token)."""
import httpx

import config
from errors import InvalidEvent, TransientError


class OrderClient:
    def __init__(self, base_url: str | None = None, service_token: str | None = None,
                 timeout: float | None = None, transport: httpx.BaseTransport | None = None):
        self._client = httpx.Client(
            base_url=base_url or config.ORDER_SERVICE_URL,
            timeout=timeout or config.HTTP_TIMEOUT,
            transport=transport,
        )
        self._token = service_token if service_token is not None else config.SERVICE_TOKEN

    def set_status(self, order_id: str, status: str) -> bool:
        """Devuelve True si el estado se aplicó, False si ya estaba (evento repetido/desordenado)."""
        try:
            response = self._client.post(
                f"/internal/orders/{order_id}/status",
                json={"status": status},
                headers={"X-Service-Token": self._token},
            )
        except httpx.HTTPError as exc:
            raise TransientError(f"Order Service no responde: {exc}") from exc
        if response.status_code == 200:
            return bool(response.json().get("applied"))
        if response.status_code == 404:
            raise InvalidEvent(f"El pedido {order_id} no existe")
        # 409: el pago aún no se reflejó (carrera); 401/5xx: configuración o caída → reintentar.
        raise TransientError(f"Order Service respondió {response.status_code}: {response.text[:300]}")
