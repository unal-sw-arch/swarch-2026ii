from rest_framework.serializers import ModelSerializer, ValidationError
from .models import User


class UserSerializer(ModelSerializer):

    class Meta:
        model = User
        fields = [
            "id",
            "email",
            "name",
            "faculty",
            "status",
            "is_active",
            "role",
            "date_joined",
        ]
        read_only_fields = ["id", "email", "name", "date_joined"]

    def validate(self, data):
        role = data.get("role", self.instance.role if self.instance else None)
        faculty = data.get("faculty", self.instance.faculty if self.instance else None)

        if role == User.Role.IMPLEMENT_ADMIN and not faculty:
            raise ValidationError(
                {
                    "faculty": "Debe asignar una facultad al Administrador de Implementos."
                }
            )

        return data
