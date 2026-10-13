from rest_framework import serializers
from .models import Borrowing, Implement, Reserve, User


class UserSummarySerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ["id", "name", "email"]


class ImplementSerializer(serializers.ModelSerializer):
    image_file = serializers.ImageField(write_only=True, required=False, allow_null=True)

    class Meta:
        model = Implement
        fields = [
            "id",
            "name",
            "category",
            "faculty",
            "state",
            "description",
            "image",
            "image_file",
        ]
        read_only_fields = ["id", "image"]

    def validate_state(self, value):
        if value in ("RES", "PRE"):
            raise serializers.ValidationError(
                "El estado Reservado y Prestado solo puede ser asignado por el sistema."
            )
        return value

    def create(self, validated_data):
        validated_data.pop("image_file", None)
        return super().create(validated_data)

    def update(self, instance, validated_data):
        validated_data.pop("image_file", None)
        return super().update(instance, validated_data)


class ReserveSerializer(serializers.ModelSerializer):
    implement = serializers.PrimaryKeyRelatedField(queryset=Implement.objects.all())
    implement_name = serializers.CharField(source="implement.name", read_only=True)
    implement_state = serializers.CharField(source="implement.state", read_only=True)
    user = UserSummarySerializer(read_only=True)

    class Meta:
        model = Reserve
        fields = [
            "id",
            "implement",
            "implement_name",
            "implement_state",
            "user",
            "datetime_reserved",
            "datetime_expiration",
            "active",
        ]
        read_only_fields = [
            "id",
            "implement_name",
            "implement_state",
            "user",
            "datetime_reserved",
            "datetime_expiration",
            "active",
        ]

    def validate_implement(self, implement):
        if implement.state != "DIS":
            raise serializers.ValidationError(
                "Este implemento no está disponible para reservar."
            )
        return implement

    def validate(self, data):
        request = self.context.get("request")
        user = request.user if request else None

        if user and user.is_authenticated:
            if Reserve.objects.filter(user=user, active=True).exists():
                raise serializers.ValidationError(
                    "Ya tienes una reserva activa. Solo se permite una reserva a la vez."
                )

            if Borrowing.objects.filter(user=user, active=True).exists():
                raise serializers.ValidationError(
                    "Tienes un préstamo activo. No puedes realizar una reserva."
                )

        return data

    def to_representation(self, instance):
        data = super().to_representation(instance)
        data["implement"] = ImplementSerializer(instance.implement).data
        data["user"] = UserSummarySerializer(instance.user).data
        return data


class ReserveAdminSerializer(ReserveSerializer):
    user_email = serializers.CharField(source="user.email", read_only=True)
    user_name = serializers.CharField(source="user.name", read_only=True)

    class Meta(ReserveSerializer.Meta):
        fields = ReserveSerializer.Meta.fields + ["user_email", "user_name"]


class BorrowingSerializer(serializers.ModelSerializer):
    implement = serializers.PrimaryKeyRelatedField(queryset=Implement.objects.all())
    implement_name = serializers.CharField(source="implement.name", read_only=True)
    user = UserSummarySerializer(read_only=True)
    user_email = serializers.CharField(source="user.email", read_only=True)
    user_name = serializers.CharField(source="user.name", read_only=True)

    class Meta:
        model = Borrowing
        fields = [
            "id",
            "implement",
            "implement_name",
            "user",
            "user_email",
            "user_name",
            "datetime_borrowed",
            "datetime_return",
            "active",
        ]
        read_only_fields = [
            "id",
            "implement_name",
            "user",
            "user_email",
            "user_name",
            "datetime_borrowed",
        ]

    def to_representation(self, instance):
        data = super().to_representation(instance)
        data["implement"] = ImplementSerializer(instance.implement).data
        data["user"] = UserSummarySerializer(instance.user).data
        return data
