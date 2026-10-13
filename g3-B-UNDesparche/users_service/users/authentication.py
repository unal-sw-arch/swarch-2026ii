from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed

from firebase_admin import _apps, initialize_app
from firebase_admin import auth as firebase_auth
from firebase_admin import credentials

from django.conf import settings

from .models import User

if not _apps:
    cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
    initialize_app(cred)


class FirebaseAuthentication(BaseAuthentication):
    """
    Clase de autenticación personalizada para Django Rest Framework que utiliza Firebase para autenticar usuarios.
    """

    def authenticate(self, request):
        auth_header = request.headers.get("Authorization")
        if not auth_header:
            return None

        id_token = auth_header.split(" ").pop()

        # En modo desarrollo (DEBUG=True), permitir autenticación con tokens demo / mock
        if settings.DEBUG and (id_token.startswith("mock_") or id_token.startswith("demo_")):
            user = None
            if id_token.startswith("demo_user_"):
                email = id_token.replace("demo_user_", "")
                user = User.objects.filter(email=email).first()
            elif id_token.startswith("mock_user_"):
                val = id_token.replace("mock_user_", "")
                if val.isdigit():
                    user = User.objects.filter(id=int(val)).first()
                else:
                    user = User.objects.filter(email=val).first()

            if not user:
                user = User.objects.filter(role=User.Role.SYS_ADMIN).first() or User.objects.first()

            if user:
                decoded_token = {
                    "uid": user.firebase_uid or f"dev_uid_{user.id}",
                    "email": user.email,
                    "name": user.name,
                    "role": user.role,
                    "email_verified": True,
                }
                return (user, decoded_token)

        try:
            decoded_token = firebase_auth.verify_id_token(id_token)
        except firebase_auth.InvalidIdTokenError:
            raise AuthenticationFailed("Token de Firebase invalido.")
        except Exception as e:
            raise AuthenticationFailed(f"No se pudo verificar el token.")

        name = decoded_token.get("name", "")
        email = decoded_token.get("email")
        firebase_uid = decoded_token.get("uid")

        if not email or not email.endswith("@unal.edu.co"):
            raise AuthenticationFailed(
                "El correo debe pertenecer al dominio @unal.edu.co"
            )

        if not decoded_token.get("email_verified"):
            raise AuthenticationFailed("Debes verificar tu correo electrónico.")

        user, created = User.objects.get_or_create(
            email=email,
            defaults={
                "firebase_uid": firebase_uid,
                "name": name,
            },
        )

        if not created:
            updated_fields = []
            if user.firebase_uid != firebase_uid:
                user.firebase_uid = firebase_uid
                updated_fields.append("firebase_uid")

            if user.name != name and name:
                user.name = name
                updated_fields.append("name")

            if updated_fields:
                user.save(update_fields=updated_fields)

        if not user.is_active:
            raise AuthenticationFailed("Usuario inactivo.")

        return (user, decoded_token)
