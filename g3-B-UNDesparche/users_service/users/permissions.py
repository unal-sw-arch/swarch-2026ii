from rest_framework.permissions import BasePermission
from .models import User


class IsSystemAdmin(BasePermission):

    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.role == User.Role.SYS_ADMIN


class IsSystemAdminOrImplementAdmin(BasePermission):

    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.role in [
            User.Role.SYS_ADMIN,
            User.Role.IMPLEMENT_ADMIN,
        ]
