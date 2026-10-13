from datetime import timedelta
from django.core.management.base import BaseCommand
from django.utils import timezone
from inventory.models import Borrowing, Implement, Reserve, User


class Command(BaseCommand):
    help = "Puebla la base de datos con los usuarios, implementos, reservas y préstamos mock iniciales"

    def handle(self, *args, **options):
        now = timezone.now()

        # 1. Seed Users
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

        users_dict = {}
        for u in mock_users:
            email = u.pop("email")
            user_obj, _ = User.objects.update_or_create(email=email, defaults=u)
            users_dict[email] = user_obj

        self.stdout.write(f"Usuarios procesados: {len(users_dict)}")

        # 2. Seed Implements
        mock_implements = [
            {
                "name": "Balón de Baloncesto Spalding TF-1000 #7",
                "description": "Balón de cuero sintético para cancha múltiple exterior o coliseo. En excelente estado de presión y agarre.",
                "category": "BAL",
                "state": "DIS",
                "faculty": "ING",
                "image": "https://images.unsplash.com/photo-1519861531473-9200262188bf?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Set de Raquetas y Pelotas de Tenis de Mesa",
                "description": "Incluye 2 raquetas de control con goma lisa y 3 pelotas Donic 3 estrellas.",
                "category": "RAQ",
                "state": "DIS",
                "faculty": "ING",
                "image": "https://images.unsplash.com/photo-1534158914592-062992fbe900?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Mesa Plegable Profesional de Ping Pong",
                "description": "Mesa reglamentaria de 25mm con ruedas de traslado y red tensora instalada en el hall.",
                "category": "MES",
                "state": "RES",
                "faculty": "ING",
                "image": "https://images.unsplash.com/photo-1609710228159-0fa9bd7c0827?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Ajedrez de Madera Profesional Staunton #5",
                "description": "Tablero enrollable de vinilo pesado y piezas de madera con doble dama para competición.",
                "category": "JUM",
                "state": "DIS",
                "faculty": "CCS",
                "image": "https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Balón de Voleibol Mikasa MVA200 Profesional",
                "description": "Diseño en espiral de 8 paneles para vóley sala o prado. Préstamo por 2 horas máximo.",
                "category": "BAL",
                "state": "PRE",
                "faculty": "MED",
                "image": "https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Juego de Mesa Catán Edición Especial",
                "description": "Juego completo de colonos con tablero modular de hexágonos, cartas y dados intactos.",
                "category": "JUM",
                "state": "DIS",
                "faculty": "CHS",
                "image": "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Set de Frisbees Ultimate Discraft 175g",
                "description": "Discos oficiales de competición para entrenamiento en la zona de canchas.",
                "category": "JUR",
                "state": "DIS",
                "faculty": "ART",
                "image": None,
            },
            {
                "name": "Balón de Fútbol #5 Golty Origen Balón de Oro",
                "description": "Balón cosido a mano para grama natural o sintética. En mantenimiento por costura.",
                "category": "BAL",
                "state": "NDS",
                "faculty": "ING",
                "image": "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?auto=format&fit=crop&w=600&q=80",
            },
            {
                "name": "Cronómetro Digital Profesional con Memoria",
                "description": "Ideal para pruebas de atletismo o toma de tiempos en eventos deportivos de salud.",
                "category": "OTS",
                "state": "DIS",
                "faculty": "ENF",
                "image": None,
            },
        ]

        implements_dict = {}
        for item in mock_implements:
            name = item.pop("name")
            imp_obj, _ = Implement.objects.update_or_create(name=name, defaults=item)
            implements_dict[name] = imp_obj

        self.stdout.write(f"Implementos procesados: {len(implements_dict)}")

        # 3. Seed Reserves
        mesa = implements_dict.get("Mesa Plegable Profesional de Ping Pong")
        estudiante = users_dict.get("dvelasquezg@unal.edu.co")
        if mesa and estudiante:
            Reserve.objects.filter(implement=mesa, user=estudiante).delete()
            Reserve.objects.create(
                implement=mesa,
                user=estudiante,
                datetime_reserved=now - timedelta(minutes=2),
                datetime_expiration=now + timedelta(minutes=8),
                active=True,
            )
            mesa.state = "RES"
            mesa.save(update_fields=["state"])
            self.stdout.write("Reserva mock creada para David Velásquez.")

        # 4. Seed Borrowings
        voley = implements_dict.get("Balón de Voleibol Mikasa MVA200 Profesional")
        santiago = users_dict.get("smorales@unal.edu.co")
        if voley and santiago:
            Borrowing.objects.filter(implement=voley, user=santiago).delete()
            Borrowing.objects.create(
                implement=voley,
                user=santiago,
                datetime_borrowed=now - timedelta(minutes=45),
                datetime_return=None,
                active=True,
            )
            voley.state = "PRE"
            voley.save(update_fields=["state"])
            self.stdout.write("Préstamo mock creado.")

        self.stdout.write(self.style.SUCCESS("¡Inventario sembrado exitosamente!"))
