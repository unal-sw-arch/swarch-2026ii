from rest_framework.permissions import BasePermission

SYS_ADMIN = "SYS_ADMIN"
IMPLEMENT_ADMIN = "IMPLEMENT_ADMIN"


class IsSystemAdminOrImplementAdmin(BasePermission):
    """
    El usuario debe ser Administrador del Sistema, o Administrador de Implementos
    y el implemento debe pertenecer a su misma facultad.
    """

    def has_permission(self, request, view):
        user = request.user
        return bool(user.is_authenticated and user.role in (SYS_ADMIN, IMPLEMENT_ADMIN))

    def has_object_permission(self, request, view, obj):
        user = request.user
        if not user.is_authenticated:
            return False

        # El Administrador del Sistema puede trabajar con cualquier implemento
        if user.role == SYS_ADMIN:
            return True

        # Un administrador de implementos sin facultad no puede gestionar nada
        if not user.faculty:
            return False

        # Implement tiene facultad propia; Reserve y Borrowing la heredan de su implemento
        if hasattr(obj, "faculty"):
            return obj.faculty == user.faculty

        if hasattr(obj, "implement"):
            return obj.implement.faculty == user.faculty

        return False
