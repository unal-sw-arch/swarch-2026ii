from django.db import IntegrityError, transaction
from django.db.models import Q
from django_filters.rest_framework import DjangoFilterBackend

from rest_framework import filters, status, viewsets
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.decorators import action, api_view, permission_classes

from .models import Event, Subscription
from .serializers import EmailSubscriptionSerializer, EventSerializer
from .permissions import (
    SYS_ADMIN,
    IsEventOwner,
    IsRegisteredUser,
    IsSystemAdminOrEventAdmin,
)

from core.firebase_storage import delete_image, upload_image


class EventViewSet(viewsets.ModelViewSet):

    serializer_class = EventSerializer
    http_method_names = ["get", "post", "patch", "delete"]
    filter_backends = [
        DjangoFilterBackend,
        filters.SearchFilter,
        filters.OrderingFilter,
    ]
    filterset_fields = {
        "category": ["exact", "in"],
        "status": ["exact", "in"],
        "published": ["exact", "in"],
    }
    search_fields = ["name", "place"]
    ordering_fields = ["name", "datetime_start"]
    ordering = ["datetime_start"]

    def get_permissions(self):
        if self.action in ["list", "retrieve", "subscribe", "unsubscribe"]:
            return [AllowAny()]

        if self.action in ["create"]:
            return [IsRegisteredUser(), IsSystemAdminOrEventAdmin()]

        if self.action in ["partial_update", "publish", "destroy"]:
            return [IsRegisteredUser(), IsSystemAdminOrEventAdmin(), IsEventOwner()]

        return [IsRegisteredUser()]

    def get_queryset(self):
        queryset = Event.objects.all()
        user = self.request.user

        if user.is_anonymous:
            return queryset.filter(published=True)

        user_id_str = str(getattr(user, "id", ""))
        user_uid = getattr(user, "firebase_uid", None) or getattr(user, "uid", None) or user_id_str
        return queryset.filter(Q(published=True) | Q(organizer_id=user_uid) | Q(organizer_id=user_id_str))

    def perform_create(self, serializer):
        image_file = self.request.FILES.get("image_file")
        image_url = upload_image(image_file, folder="events") if image_file else None
        user = self.request.user
        organizer_id = getattr(user, "firebase_uid", None) or getattr(user, "uid", None) or str(user.id)
        serializer.save(
            organizer_id=organizer_id,
            organizer_name=user.name or user.email,
            image=image_url,
        )

    def perform_update(self, serializer):
        event = self.get_object()
        if not event.is_editable():
            raise PermissionDenied(
                "No es posible modificar un evento publicado que este cancelado o finalizado."
            )

        previous_state = event.status
        previous_datetime_start = event.datetime_start

        image_file = self.request.FILES.get("image_file")
        if image_file:
            if event.image:
                delete_image(event.image)
            image_url = upload_image(image_file, folder="events")
            updated_event = serializer.save(image=image_url)
        else:
            updated_event = serializer.save()

    def perform_destroy(self, instance):
        is_system_admin = self.request.user.role == SYS_ADMIN

        if instance.published and not is_system_admin:
            raise PermissionDenied(
                "Solo el administrador del sistema puede eliminar eventos publicados."
            )

        if instance.image:
            delete_image(instance.image)
        super().perform_destroy(instance)

    @action(detail=True, methods=["post"])
    def publish(self, request, pk=None):
        event = self.get_object()

        if event.published:
            raise ValidationError({"detail": "El evento ya esta publicado."})

        if event.status in ["CAN", "FIN"]:
            raise ValidationError(
                {"detail": ("No es posible publicar un evento cancelado o finalizado.")}
            )

        event.published = True
        event.save(update_fields=["published"])
        serializer = self.get_serializer(event)

        return Response(serializer.data)

    @action(detail=True, methods=["post"], url_path="subscribe")
    def subscribe(self, request, pk=None):
        event = self.get_object()
        user = request.user

        if not event.published:
            raise ValidationError(
                {"detail": "Solo es posible suscribirse a eventos publicados."}
            )

        if event.status in ["CAN", "FIN"]:
            raise ValidationError(
                {
                    "detail": "No es posible suscribirse a un evento cancelado o finalizado."
                }
            )

        if user.is_authenticated:
            user_uid = getattr(user, "uid", None) or getattr(user, "firebase_uid", None) or str(user.id)
            if Subscription.objects.filter(event=event, user_id=user_uid).exists():
                raise ValidationError({"detail": "Ya estás suscrito a este evento."})

            try:
                with transaction.atomic():
                    subscription = Subscription.objects.create(
                        event=event,
                        user_id=user_uid,
                        notification_email=user.email or "",
                    )
            except IntegrityError:
                raise ValidationError(
                    {"detail": "Este usuario o correo ya está suscrito a este evento."}
                )
        else:
            serializer = EmailSubscriptionSerializer(data=request.data)
            serializer.is_valid(raise_exception=True)
            email = serializer.validated_data["email"]

            if Subscription.objects.filter(
                event=event, notification_email=email
            ).exists():
                raise ValidationError(
                    {"detail": "Ese correo ya está suscrito a este evento."}
                )
            subscription = Subscription.objects.create(
                event=event, notification_email=email
            )

        return Response(
            {"detail": "Suscripción realizada correctamente."},
            status=status.HTTP_201_CREATED,
        )

    @action(detail=True, methods=["post"], url_path="unsubscribe")
    def unsubscribe(self, request, pk=None):
        event = self.get_object()
        user = request.user

        if user.is_authenticated:
            user_uid = getattr(user, "uid", None) or getattr(user, "firebase_uid", None) or str(user.id)
            subscription = Subscription.objects.filter(
                event=event, user_id=user_uid
            ).first()
            if subscription is None:
                raise ValidationError({"detail": "No estás suscrito a este evento."})
        else:
            serializer = EmailSubscriptionSerializer(data=request.data)
            serializer.is_valid(raise_exception=True)
            email = serializer.validated_data["email"]
            subscription = Subscription.objects.filter(
                event=event, notification_email=email
            ).first()

            if subscription is None:
                raise ValidationError(
                    {"detail": "Ese correo no está suscrito a este evento."}
                )

        subscription.delete()
        return Response({"detail": "Suscripción cancelada correctamente."})


@api_view(["GET"])
@permission_classes([AllowAny])
def unsubscribe_via_token(request):
    token = request.query_params.get("token")
    if not token:
        return Response(
            {"detail": "Falta el token de desuscripción."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        from .tokens import read_unsubscribe_token
        from django.core import signing
        data = read_unsubscribe_token(token)
    except Exception:
        return Response(
            {"detail": "El enlace de desuscripción no es válido o está incompleto."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    subscription = (
        Subscription.objects.select_related("event")
        .filter(id=data.get("sid"))
        .first()
    )

    if subscription is None:
        return Response(
            {
                "detail": "Esta suscripción ya había sido cancelada.",
                "event_name": None,
                "already": True,
            }
        )

    event_name = subscription.event.name
    subscription.delete()

    return Response(
        {
            "detail": "Te desuscribiste correctamente de este evento.",
            "event_name": event_name,
            "already": False,
        }
    )

