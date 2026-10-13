from django.core.management.base import BaseCommand
from users.models import User

class Command(BaseCommand):
    help = "Puebla la base de datos con los usuarios requeridos de UNDesparche"

    def handle(self, *args, **options):
        mock_users = [
            {
                "email": "dbarragang@unal.edu.co",
                "name": "Daniel Barragán",
                "faculty": "ING",
                "status": "ACT",
                "role": "SYS_ADMIN",
                "firebase_uid": "mock_uid_dbarragang",
                "is_staff": True,
                "is_superuser": True,
            },
            {
                "email": "jalzateco@unal.edu.co",
                "name": "Julián Alzate",
                "faculty": "ING",
                "status": "ACT",
                "role": "IMPLEMENT_ADMIN",
                "firebase_uid": "mock_uid_jalzateco",
                "is_staff": True,
                "is_superuser": False,
            },
            {
                "email": "vcanonc@unal.edu.co",
                "name": "Víctor Canon",
                "faculty": "ING",
                "status": "ACT",
                "role": "EVENT_ADMIN",
                "firebase_uid": "mock_uid_vcanonc",
                "is_staff": True,
                "is_superuser": False,
            },
            {
                "email": "dvelasquezg@unal.edu.co",
                "name": "David Velásquez",
                "faculty": "ING",
                "status": "ACT",
                "role": "COMMUNITY",
                "firebase_uid": "mock_uid_dvelasquezg",
                "is_staff": False,
                "is_superuser": False,
            },
            {
                "email": "smorales@unal.edu.co",
                "name": "Santiago Morales",
                "faculty": "MED",
                "status": "SAN",
                "role": "COMMUNITY",
                "firebase_uid": "mock_uid_smorales",
                "is_staff": False,
                "is_superuser": False,
            },
        ]

        count = 0
        for data in mock_users:
            email = data.pop("email")
            user, created = User.objects.update_or_create(
                email=email,
                defaults=data
            )
            # Asegurar contraseña utilizable para admin si es necesario
            user.set_password("Admin123*")
            user.save(update_fields=["password"])
            count += 1
            action = "creado" if created else "actualizado"
            self.stdout.write(f"Usuario {email} ({user.role}) {action}.")

        self.stdout.write(self.style.SUCCESS(f"Se procesaron {count} usuarios exitosamente."))
