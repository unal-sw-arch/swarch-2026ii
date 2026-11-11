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


class UserCreateSerializer(ModelSerializer):
    """
    Solo para el alta de usuarios (primer login, la hace el gateway).

    Acepta únicamente la identidad (firebase_uid, email, name). Rol, estado y facultad
    NO se aceptan aunque vengan en el body: salen de los valores por defecto del modelo
    (COMMUNITY, activo, sin facultad). Así, esta ruta nunca puede crear un administrador;
    los roles se asignan después con PATCH por un administrador del sistema.
    """

    class Meta:
        model = User
        fields = ["firebase_uid", "email", "name"]
        extra_kwargs = {
            # En el modelo es opcional (null=True), pero un usuario nuevo siempre debe traerlo
            "firebase_uid": {
                "required": True,
                "allow_null": False,
                "allow_blank": False,
            },
        }

    def create(self, validated_data):
        # create_user normaliza el correo y deja la contraseña inutilizable
        # (el único inicio de sesión es con Google, a través de Firebase)
        return User.objects.create_user(**validated_data)
