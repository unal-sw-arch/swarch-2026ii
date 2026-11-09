from datetime import timedelta
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import filters, mixins, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from django_filters.rest_framework import DjangoFilterBackend

from core.firebase import init_firebase
from core.firebase_storage import delete_image, upload_image
from .models import Borrowing, Implement, Reserve
from .permissions import IsSystemAdminOrImplementAdmin
from .serializers import (
    BorrowingSerializer,
    ImplementSerializer,
    ReserveAdminSerializer,
    ReserveSerializer,
)


def _upload_implement_image(image_file):
    """Sube la imagen de un implemento a Firebase Storage y devuelve su URL."""
    init_firebase()  # Storage sigue usando Firebase; ya no lo inicializa la autenticación
    return upload_image(image_file, folder="inventory")


def _delete_implement_image(image_url):
    """Elimina la imagen de un implemento de Firebase Storage."""
    init_firebase()
    delete_image(image_url)


class ImplementViewSet(viewsets.ModelViewSet):
    serializer_class = ImplementSerializer
    filter_backends = [DjangoFilterBackend, filters.SearchFilter]
    filterset_fields = {"category": ["exact"], "faculty": ["exact"], "state": ["exact"]}
    search_fields = ["name", "description"]
    http_method_names = ["get", "post", "patch", "delete"]

    def get_queryset(self):
        return Implement.objects.all().order_by("name")

    def get_permissions(self):
        if self.action in ["list", "retrieve"]:
            return [AllowAny()]
        return [IsAuthenticated(), IsSystemAdminOrImplementAdmin()]

    def perform_create(self, serializer):
        image_file = self.request.FILES.get("image_file")
        try:
            image_url = _upload_implement_image(image_file) if image_file else None
        except Exception:
            image_url = None

        user = self.request.user

        if user.is_sys_admin:
            faculty = self.request.data.get("faculty")
            if not faculty:
                raise ValidationError(
                    {
                        "faculty": "El Administrador del Sistema debe especificar la facultad del implemento."
                    }
                )
        else:
            faculty = user.faculty

        serializer.save(faculty=faculty, image=image_url)

    def perform_update(self, serializer):
        instance = self.get_object()
        self.check_object_permissions(self.request, instance)

        user = self.request.user

        if user.is_sys_admin:
            faculty = self.request.data.get("faculty", instance.faculty)
        else:
            faculty = instance.faculty

        image_file = self.request.FILES.get("image_file")
        if image_file:
            if instance.image:
                try:
                    _delete_implement_image(instance.image)
                except Exception:
                    pass
            try:
                image_url = _upload_implement_image(image_file)
            except Exception:
                image_url = instance.image
            serializer.save(image=image_url, faculty=faculty)
        else:
            serializer.save(faculty=faculty)

    def perform_destroy(self, instance):
        if instance.image:
            try:
                _delete_implement_image(instance.image)
            except Exception:
                pass
        return super().perform_destroy(instance)


class ReserveViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    mixins.CreateModelMixin,
    viewsets.GenericViewSet,
):
    http_method_names = ["get", "post"]

    def get_queryset(self):
        user = self.request.user
        if not user.is_authenticated:
            return Reserve.objects.none()

        if user.is_sys_admin:
            return Reserve.objects.all().select_related("implement")

        if user.is_implement_admin:
            return Reserve.objects.filter(
                active=True, implement__faculty=user.faculty
            ).select_related("implement")

        return Reserve.objects.filter(user_id=user.uid).select_related("implement")

    def get_permissions(self):
        if self.action in ["create", "list", "retrieve"]:
            return [IsAuthenticated()]
        return [IsAuthenticated(), IsSystemAdminOrImplementAdmin()]

    def get_serializer_class(self):
        user = self.request.user
        if user.is_authenticated and (user.is_sys_admin or user.is_implement_admin):
            return ReserveAdminSerializer
        return ReserveSerializer

    def create(self, request, *args, **kwargs):
            # RF-11: solo reservan los usuarios activos que aceptan el aviso de descargo de responsabilidad
            # Estado "Activo" de un usuario en el servicio Users
            if request.user.status != "ACT":
                raise PermissionDenied("Tu cuenta está sancionada: no puedes reservar implementos.")

            return super().create(request, *args, **kwargs)

    def perform_create(self, serializer):
        implement_id = self.request.data.get("implement")
        if not implement_id:
            raise ValidationError(
                {"implement": "Debe especificar el id del implemento a reservar."}
            )

        user = self.request.user

        try:
            with transaction.atomic():
                implement = Implement.objects.select_for_update().get(pk=implement_id)

                if implement.state != Implement.State.AVAILABLE:
                    raise ValidationError(
                        {
                            "implement": "Este implemento no está disponible para reservar."
                        }
                    )
                if Reserve.objects.filter(user_id=user.uid, active=True).exists():
                    raise ValidationError({"detail": "Ya tienes una reserva activa."})
                if Borrowing.objects.filter(user_id=user.uid, active=True).exists():
                    raise ValidationError(
                        {
                            "detail": "Tienes un préstamo activo. No puedes reservar otro implemento."
                        }
                    )

                now = timezone.now()
                reserve = serializer.save(
                    user_id=user.uid,
                    user_email=user.email,
                    implement=implement,
                    datetime_reserved=now,
                    datetime_expiration=now + timedelta(minutes=10),
                    active=True,
                )
                implement.state = Implement.State.RESERVED
                implement.save(update_fields=["state"])
                return reserve
        except Implement.DoesNotExist:
            raise ValidationError({"implement": "Implemento no encontrado."})
        except IntegrityError:
            raise ValidationError(
                {"detail": "Ya tienes una reserva o préstamo activo."}
            )

    @action(detail=True, methods=["post"], url_path="confirm")
    def confirm(self, request, pk=None):
        reserve = self.get_object()
        if not reserve.active:
            raise ValidationError({"detail": "Esta reserva ya no está activa."})

        try:
            with transaction.atomic():
                reserve.active = False
                reserve.save(update_fields=["active"])
                borrowing = Borrowing.objects.create(
                    user_id=reserve.user_id,
                    user_email=reserve.user_email,
                    implement=reserve.implement,
                    datetime_borrowed=timezone.now(),
                    active=True,
                )
                reserve.implement.state = Implement.State.BORROWED
                reserve.implement.save(update_fields=["state"])
        except IntegrityError:
            raise ValidationError({"detail": "El usuario ya tiene un préstamo activo."})

        return Response(BorrowingSerializer(borrowing).data)

    @action(detail=True, methods=["post"], url_path="cancel")
    def cancel(self, request, pk=None):
        reserve = self.get_object()
        if not reserve.active:
            raise ValidationError({"detail": "Esta reserva ya no está activa."})

        with transaction.atomic():
            reserve.active = False
            reserve.save(update_fields=["active"])
            reserve.implement.state = Implement.State.AVAILABLE
            reserve.implement.save(update_fields=["state"])

        return Response({"detail": "Reserva cancelada correctamente."})


class BorrowingViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    viewsets.GenericViewSet,
):
    http_method_names = ["get", "post"]
    serializer_class = BorrowingSerializer

    def get_queryset(self):
        user = self.request.user
        if not user.is_authenticated:
            return Borrowing.objects.none()

        if user.is_sys_admin:
            return Borrowing.objects.all().select_related("implement")

        if user.is_implement_admin:
            return Borrowing.objects.filter(
                implement__faculty=user.faculty
            ).select_related("implement")

        return Borrowing.objects.filter(user_id=user.uid).select_related("implement")

    def get_permissions(self):
        if self.action in ["list", "retrieve"]:
            return [IsAuthenticated()]
        return [IsAuthenticated(), IsSystemAdminOrImplementAdmin()]

    @action(detail=True, methods=["post"], url_path="return")
    def return_implement(self, request, pk=None):
        borrowing = self.get_object()
        if not borrowing.active:
            raise ValidationError({"detail": "Este préstamo ya fue cerrado."})

        with transaction.atomic():
            borrowing.active = False
            borrowing.datetime_return = timezone.now()
            borrowing.save(update_fields=["active", "datetime_return"])
            borrowing.implement.state = Implement.State.AVAILABLE
            borrowing.implement.save(update_fields=["state"])

        return Response({"detail": "Devolución registrada correctamente."})
