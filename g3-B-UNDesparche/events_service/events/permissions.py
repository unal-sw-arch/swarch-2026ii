from rest_framework.permissions import BasePermission

SYS_ADMIN = "SYS_ADMIN"
EVENT_ADMIN = "EVENT_ADMIN"


class IsRegisteredUser(BasePermission):
    """Usuario autenticado por el gateway y con un rol asignado."""

    def has_permission(self, request, view):
        return bool(request.user.is_authenticated and request.user.role)


class IsSystemAdminOrEventAdmin(BasePermission):

    def has_permission(self, request, view):
        return bool(
            request.user.is_authenticated
            and request.user.role in (SYS_ADMIN, EVENT_ADMIN)
        )


class IsEventOwner(BasePermission):
    """El administrador del sistema puede con cualquier evento; los demás, solo con los suyos."""

    def has_object_permission(self, request, view, obj):
        if not request.user.is_authenticated:
            return False
        if request.user.role == SYS_ADMIN:
            return True
        return obj.organizer_id == request.user.uid
