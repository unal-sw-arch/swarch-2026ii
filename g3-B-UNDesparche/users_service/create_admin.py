"""
Administradores del sistema para las pruebas.

Ejecutar desde la carpeta que contiene manage.py. Se puede correr varias veces.

  python crear_admin.py
      Crea (o repara) el administrador "admin-uid" que usa probar_users.ps1.

  python crear_admin.py correo@unal.edu.co
      Convierte en administrador del sistema a un usuario que YA existe (es decir,
      que ya entró al menos una vez con Google a través del gateway). Sirve para
      probar el gateway con tu propia cuenta como administrador.
"""
import os
import sys

import django

# Debe configurarse antes de importar los modelos
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "users_service.settings")
django.setup()

from users.models import User  # noqa: E402

ADMIN_UID = "admin-uid"
ADMIN_EMAIL = "admin@unal.edu.co"


def promote_existing_user(email: str) -> int:
    user = User.objects.filter(email__iexact=email).first()
    if user is None:
        print(f"No existe ningún usuario con el correo {email}.")
        print("Primero debe iniciar sesión una vez (por ejemplo, llamando a /users/me/ en el gateway).")
        return 1
    user.role = User.Role.SYS_ADMIN
    user.is_active = True
    user.save()
    print(f"{user.email} ahora es administrador del sistema (id={user.id}).")
    return 0


def ensure_test_admin() -> int:
    admin = (
        User.objects.filter(firebase_uid=ADMIN_UID).first()
        or User.objects.filter(email=ADMIN_EMAIL).first()
    )
    if admin is None:
        admin = User.objects.create_user(
            email=ADMIN_EMAIL,
            firebase_uid=ADMIN_UID,
            name="Admin Prueba",
            role=User.Role.SYS_ADMIN,
        )
        print(f"Administrador creado (id={admin.id}, uid={ADMIN_UID}).")
    else:
        # Ya existía: se deja con el uid, el rol y el estado que necesita el script de pruebas
        admin.firebase_uid = ADMIN_UID
        admin.role = User.Role.SYS_ADMIN
        admin.is_active = True
        admin.save()
        print(f"Administrador ya existía; se dejó listo (id={admin.id}, uid={ADMIN_UID}).")
    return 0


if __name__ == "__main__":
    if len(sys.argv) > 1:
        sys.exit(promote_existing_user(sys.argv[1]))
    sys.exit(ensure_test_admin())
