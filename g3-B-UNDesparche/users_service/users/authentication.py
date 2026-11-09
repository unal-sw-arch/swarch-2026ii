from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed

from .models import User

# Headers que inyecta el API Gateway (se definen aquí, en un solo lugar)
GATEWAY_HEADER = "X-From-Gateway"
USER_ID_HEADER = "X-User-Id"


def is_gateway_request(request) -> bool:
    """Indica si la petición dice venir del API Gateway."""
    return request.headers.get(GATEWAY_HEADER) == "true"


class GatewayAuthentication(BaseAuthentication):
    """
    Identifica al usuario a partir de los headers que inyecta el API Gateway.

    El gateway ya verificó el token de Firebase, así que aquí solo se confía en
    X-From-Gateway y se LEE al usuario de la BD usando X-User-Id (su firebase_uid).
    Nunca se crea ni se actualiza nada: la BD es la fuente de verdad, y los demás
    headers (rol, estado...) pueden venir desactualizados desde el caché del gateway.
    """

    def authenticate(self, request):
        if not is_gateway_request(request):
            return None  # no viene del gateway: anónimo

        firebase_uid = request.headers.get(USER_ID_HEADER)
        if not firebase_uid:
            # Llamada interna del gateway sin usuario final (consultar o crear
            # al usuario durante el login): la autorizan los permisos.
            return None

        user = User.objects.filter(firebase_uid=firebase_uid).first()
        if user is None:
            raise AuthenticationFailed("Unknown user.")
        if not user.is_active:
            # Cuenta bloqueada (is_active=False)
            raise AuthenticationFailed("User inactive or blocked.")
        return (user, None)

    def authenticate_header(self, request):
        # Hace que DRF responda 401 (y no 403) cuando falta autenticación
        return "Gateway"
