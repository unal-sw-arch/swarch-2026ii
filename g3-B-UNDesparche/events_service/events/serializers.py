from rest_framework import serializers
from django.utils import timezone
from .models import Event


class EmailSubscriptionSerializer(serializers.Serializer):
    email = serializers.EmailField(required=True)


class RoundedCoordinateField(serializers.FloatField):
    def to_internal_value(self, data):
        val = super().to_internal_value(data)
        return round(val, 6)


class EventSerializer(serializers.ModelSerializer):

    organizer = serializers.SerializerMethodField()
    is_subscribed = serializers.SerializerMethodField()
    image_file = serializers.ImageField(write_only=True, required=False, allow_null=True)
    latitude = RoundedCoordinateField()
    longitude = RoundedCoordinateField()

    class Meta:
        model = Event
        fields = [
            "id",
            "name",
            "description",
            "published",
            "place",
            "latitude",
            "longitude",
            "datetime_start",
            "datetime_end",
            "organizer",
            "status",
            "category",
            "image",
            "image_file",
            "is_subscribed",
        ]
        read_only_fields = ["id", "is_subscribed", "published", "image"]

    def get_organizer(self, obj):
        return {"id": obj.organizer_id, "name": obj.organizer_name}

    def get_is_subscribed(self, obj):
        request = self.context.get("request")
        if not request or request.user.is_anonymous:
            return False

        user_uid = getattr(request.user, "uid", None) or getattr(request.user, "firebase_uid", None) or str(getattr(request.user, "id", ""))
        return obj.subscriptions.filter(user_id=user_uid).exists()

    def validate(self, data):
        datetime_start = data.get(
            "datetime_start", self.instance.datetime_start if self.instance else None
        )
        datetime_end = data.get(
            "datetime_end", self.instance.datetime_end if self.instance else None
        )

        # Si estoy creando evento y hay una fecha inicial y esta es mayor a la actual, no hay problema
        if (
            (self.instance is None)
            and (datetime_start is not None)
            and (datetime_start < timezone.now())
        ):
            raise serializers.ValidationError(
                {
                    "datetime_start": (
                        "La fecha y hora de inicio no puede ser anterior a la fecha y hora actual."
                    )
                }
            )

        # Si hay una fecha inicial, hay una fecha final y la inicial es menor a la final, no hay problema
        if (
            (datetime_start is not None)
            and (datetime_end is not None)
            and (datetime_end <= datetime_start)
        ):
            raise serializers.ValidationError(
                {
                    "datetime_end": "La fecha y hora de finalización debe ser posterior a la fecha y hora de inicio."
                }
            )

        return data

    def create(self, validated_data):
        validated_data.pop("image_file", None)
        return super().create(validated_data)

    def update(self, instance, validated_data):
        validated_data.pop("image_file", None)
        return super().update(instance, validated_data)
