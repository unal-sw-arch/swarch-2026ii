from pydantic import BaseModel, ConfigDict


class CurrentUser(BaseModel):
    """
    Usuario autenticado tal como lo necesita el gateway.

    Es inmutable (frozen) porque la misma instancia se comparte desde el caché
    entre peticiones: así ninguna petición puede alterar los datos de otra.
    `role`, `status` e `is_active` son obligatorios a propósito: si Users no los devuelve,
    la validación falla en vez de asumir valores por defecto.
    """

    model_config = ConfigDict(frozen=True)

    firebase_uid: str
    email: str
    name: str | None = None
    role: str
    faculty: str | None = None
    status: str
    is_active: bool  # False = cuenta bloqueada (RF-29); distinto de status (sanción)
