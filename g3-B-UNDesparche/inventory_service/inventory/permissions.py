from rest_framework.permissions import BasePermission
from .models import User


class IsSystemAdminOrImplementAdmin(BasePermission):
    """
    El usuario debe ser Administrador de Implementos y
    el implemento debe pertenecer a su misma facultad,
    o simplemente ser Administrador del Sistema.
    """

    def has_permission(self, request, view):
        if not request.user or not request.user.is_authenticated:
            return False

        if getattr(request.user, "role", None) in [User.Role.SYS_ADMIN, User.Role.IMPLEMENT_ADMIN]:
            return True

        return request.user.groups.filter(
            name__in=[
                "Administrador del Sistema",
                "Administrador de Implementos",
            ]
        ).exists()

    def has_object_permission(self, request, view, obj):
        # El Administrador del Sistema puede trabajar con cualquier implemento
        if getattr(request.user, "role", None) == User.Role.SYS_ADMIN or request.user.groups.filter(name="Administrador del Sistema").exists():
            return True

        user_faculty = getattr(request.user, "faculty", None)

        if hasattr(obj, "faculty"):
            return obj.faculty == user_faculty

        if hasattr(obj, "implement"):
            return obj.implement.faculty == user_faculty

        return False
