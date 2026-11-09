from rest_framework.permissions import BasePermission

from .authentication import USER_ID_HEADER, is_gateway_request
from .models import User


def _has_role(request, roles) -> bool:
    """True si hay un usuario autenticado con alguno de los roles dados."""
    user = request.user
    return bool(user and user.is_authenticated and user.role in roles)


class IsSystemAdmin(BasePermission):

    def has_permission(self, request, view):
        return _has_role(request, (User.Role.SYS_ADMIN,))


class IsSystemAdminOrImplementAdmin(BasePermission):

    def has_permission(self, request, view):
        return _has_role(request, (User.Role.SYS_ADMIN, User.Role.IMPLEMENT_ADMIN))


class IsGatewayServiceCall(BasePermission):
    """
    Llamada interna del gateway SIN usuario final (no trae X-User-Id).
    La usa el gateway al crear al usuario en su primer login, cuando todavía no existe.
    """

    def has_permission(self, request, view):
        return is_gateway_request(request) and not request.headers.get(USER_ID_HEADER)


class IsGatewayUserLookup(IsGatewayServiceCall):
    """
    Consulta interna del gateway para buscar a UN usuario por firebase_uid.
    Exige el parámetro para que esta llamada anónima no pueda listar a todos los usuarios.
    """

    def has_permission(self, request, view):
        return super().has_permission(request, view) and bool(
            request.query_params.get("firebase_uid")
        )
