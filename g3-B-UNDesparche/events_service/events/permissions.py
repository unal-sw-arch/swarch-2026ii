from rest_framework.permissions import BasePermission

SYS_ADMIN = "SYS_ADMIN"
EVENT_ADMIN = "EVENT_ADMIN"


class IsRegisteredUser(BasePermission):

    def has_permission(self, request, view):
        return bool(request.user.role)


class IsSystemAdminOrEventAdmin(BasePermission):

    def has_permission(self, request, view):
        return request.user.role in (SYS_ADMIN, EVENT_ADMIN)


class IsEventOwner(BasePermission):

    def has_object_permission(self, request, view, obj):
        if getattr(request.user, "role", None) == SYS_ADMIN:
            return True

        user_uid = getattr(request.user, "firebase_uid", None) or getattr(request.user, "uid", None) or str(request.user.id)
        return obj.organizer_id in (user_uid, str(request.user.id))
