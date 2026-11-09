from typing import ClassVar

from rest_framework.authentication import BaseAuthentication

# Headers que inyecta el API Gateway (se definen aquí, en un solo lugar)
GATEWAY_HEADER = "X-From-Gateway"
USER_ID_HEADER = "X-User-Id"


class GatewayUser:
    """
    Usuario liviano reconstruido a partir de los headers del gateway.

    No existe en la base de datos de Events: el gateway ya verificó el token de
    Firebase y consultó a Users, así que aquí solo se leen sus datos. Expone lo
    mínimo que DRF y las vistas necesitan de un usuario.
    """

    is_authenticated: ClassVar[bool] = True
    is_anonymous: ClassVar[bool] = False
    is_active: ClassVar[bool] = True

    def __init__(self, firebase_uid, email="", role="", faculty=None, status=""):
        self.firebase_uid = firebase_uid
        self.email = email
        self.role = role
        self.faculty = faculty
        self.status = status

    @property
    def uid(self):
        """Identificador del usuario en todo el sistema (es el firebase_uid)."""
        return self.firebase_uid

    @property
    def pk(self):
        return self.firebase_uid

    def __str__(self):
        return self.email or self.firebase_uid


class GatewayAuthentication(BaseAuthentication):
    """
    Identifica al usuario con los headers que inyecta el API Gateway.

    - Sin X-From-Gateway o sin X-User-Id la petición es anónima (los endpoints
      públicos siguen funcionando).
    - Nunca toca la base de datos ni verifica tokens: eso lo hace el gateway.
    """

    def authenticate(self, request):
        if request.headers.get(GATEWAY_HEADER) != "true":
            return None  # no viene del gateway: anónimo

        firebase_uid = request.headers.get(USER_ID_HEADER)
        if not firebase_uid:
            return None  # el gateway no identificó a ningún usuario: anónimo

        user = GatewayUser(
            firebase_uid=firebase_uid,
            email=request.headers.get("X-User-Email", ""),
            role=request.headers.get("X-User-Role", ""),
            faculty=request.headers.get("X-User-Faculty") or None,
            status=request.headers.get("X-User-Status", ""),
        )
        return (user, None)

    def authenticate_header(self, request):
        # Hace que DRF responda 401 (y no 403) cuando falta autenticación
        return "Gateway"
