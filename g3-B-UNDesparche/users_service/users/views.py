from rest_framework import filters, mixins, viewsets
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.exceptions import PermissionDenied
from rest_framework.response import Response

from django_filters.rest_framework import DjangoFilterBackend

from .models import User
from .serializers import UserSerializer
from .permissions import IsSystemAdmin, IsSystemAdminOrImplementAdmin
from .services import sync_claims


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def login(request):
    # La autenticación ya verificó el token y creó o vinculó al usuario en la BD
    claims_changed = sync_claims(request.user, current_claims=request.auth)
    return Response(
        {
            "id": request.user.id,
            "role": request.user.role,
            "claims_changed": claims_changed,
        }
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def me(request):
    groups = list(request.user.groups.values_list("name", flat=True))
    return Response(
        {
            "id": request.user.id,
            "email": request.user.email,
            "name": request.user.name,
            "faculty": request.user.faculty,
            "status": request.user.status,
            "roles": request.user.role,
        }
    )


class UserViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    mixins.UpdateModelMixin,
    mixins.DestroyModelMixin,
    viewsets.GenericViewSet,
):

    queryset = User.objects.all().order_by("date_joined")
    serializer_class = UserSerializer
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_fields = {"faculty": ["exact"], "role": ["exact"], "status": ["exact"]}
    search_fields = ["email", "name"]

    def get_permissions(self):
        if self.action in ["list", "retrieve"]:
            # Ambos roles pueden listar y ver detalle
            return [IsAuthenticated(), IsSystemAdminOrImplementAdmin()]
        # Solo el admin del sistema puede modificar roles, estado y eliminar
        return [IsAuthenticated(), IsSystemAdmin()]

    def partial_update(self, request, *args, **kwargs):
        target = self.get_object()

        # Un Administrador de Sistema no puede cambiarle el rol a otro Administrador del Sistema
        if target.role == User.Role.SYS_ADMIN:
            raise PermissionDenied(
                "No se puede modificar a un Administrador del Sistema."
            )

        if request.user.role == User.Role.IMPLEMENT_ADMIN:
            allowed_fields = {"status"}
            requested_fields = set(request.data.keys())
            disallowed = requested_fields - allowed_fields

            if disallowed:
                raise PermissionDenied(
                    "El Administrador de Implementos solo puede modificar el estado del usuario."
                )

        return super().partial_update(request, *args, **kwargs)

    def destroy(self, request, *args, **kwargs):
        target = self.get_object()

        # Un Administrador de Sistema no puede ser eliminado desde este endpoint
        if target.role == User.Role.SYS_ADMIN:
            raise PermissionDenied(
                "No se puede eliminar a un Administrador del Sistema."
            )
        return super().destroy(request, *args, **kwargs)
