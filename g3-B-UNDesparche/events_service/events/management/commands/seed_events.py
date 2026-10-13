from datetime import timedelta
from django.core.management.base import BaseCommand
from django.utils import timezone
from events.models import Event, User

class Command(BaseCommand):
    help = "Puebla la base de datos con los eventos mock iniciales de UNDesparche"

    def handle(self, *args, **options):
        now = timezone.now()

        def dt(hours_delta):
            return now + timedelta(hours=hours_delta)

        mock_events = [
            {
                "name": "Torneo Relámpago de Microfútbol UNAL",
                "description": "Torneo interfacultades de microfútbol masculino y femenino. Inscripciones abiertas en las canchas de deportes. Trae tu carné estudiantil.",
                "place": "Canchas Sintéticas de Deportes",
                "latitude": 4.636800,
                "longitude": -74.085000,
                "datetime_start": dt(2),
                "datetime_end": dt(6),
                "status": "PRO",
                "category": "DEP",
                "published": True,
                "image": "https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Cineforo Freud: Ciclo Cine Latinoamericano",
                "description": "Proyección y conversatorio abierto sobre cine latinoamericano contemporáneo. Palomitas gratis para los primeros 50 asistentes.",
                "place": "Auditorio Alfonso López Pumarejo (Edif. 401)",
                "latitude": 4.637500,
                "longitude": -74.083200,
                "datetime_start": dt(-1),
                "datetime_end": dt(3),
                "status": "ECU",
                "category": "CUL",
                "published": True,
                "image": "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Asamblea General Estudiantil Sede Bogotá",
                "description": "Espacio de discusión triestamentaria sobre bienestar universitario, presupuesto y reformas académicas. Asistencia de todas las facultades.",
                "place": "Plaza Che Guevara / Auditorio León de Greiff",
                "latitude": 4.638500,
                "longitude": -74.084100,
                "datetime_start": dt(24),
                "datetime_end": dt(28),
                "status": "PRO",
                "category": "ASA",
                "published": True,
                "image": "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_dbarragang",
                "organizer_name": "Daniel Barragán",
            },
            {
                "name": "Hackathon de Software Libre y Datos Abiertos",
                "description": "48 horas de desarrollo colaborativo para crear soluciones a problemáticas del campus. Mentorías con egresados de Ingeniería de Sistemas.",
                "place": "Edificio 453 - Aulas de Ingeniería (Salón 201)",
                "latitude": 4.639100,
                "longitude": -74.082700,
                "datetime_start": dt(48),
                "datetime_end": dt(72),
                "status": "PRO",
                "category": "ACA",
                "published": True,
                "image": "https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_dbarragang",
                "organizer_name": "Daniel Barragán",
            },
            {
                "name": "Tarde de Juegos de Mesa y Desparche Recreativo",
                "description": "Ven a desconectarte entre parciales. Tenemos Catán, Carcassonne, dominó y jenga. Trae tus amigos o únete a una mesa libre.",
                "place": "Prados de la Hemeroteca Nacional",
                "latitude": 4.640200,
                "longitude": -74.086500,
                "datetime_start": dt(12),
                "datetime_end": dt(16),
                "status": "PRO",
                "category": "PAR",
                "published": True,
                "image": "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Feria Universitaria del Libro y Arte Gráfico",
                "description": "Muestra editorial independiente, fanzines, grabados e ilustraciones de estudiantes de la Facultad de Artes.",
                "place": "Edificio 301 - Escuela de Bellas Artes",
                "latitude": 4.635900,
                "longitude": -74.084600,
                "datetime_start": dt(36),
                "datetime_end": dt(42),
                "status": "PRO",
                "category": "CUL",
                "published": True,
                "image": "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Taller de Robótica Competitiva y Drones",
                "description": "Borrador de actividad preparatoria para el torneo nacional de robótica. Solo organizadores y coordinadores de laboratorio.",
                "place": "Laboratorio de Mecatrónica (Edif. 411)",
                "latitude": 4.639800,
                "longitude": -74.083000,
                "datetime_start": dt(100),
                "datetime_end": dt(104),
                "status": "PRO",
                "category": "ACA",
                "published": False,
                "image": None,
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Concierto de Jazz y Ritmos Latinos al Aire Libre",
                "description": "Presentación en vivo de ensambles estudiantiles del Conservatorio de Música. Entrada libre para toda la comunidad universitaria.",
                "place": "Plazoleta Central / Concha Acústica",
                "latitude": 4.637200,
                "longitude": -74.083800,
                "datetime_start": dt(60),
                "datetime_end": dt(64),
                "status": "PRO",
                "category": "CUL",
                "published": True,
                "image": "https://images.unsplash.com/photo-1511192336575-5a79af67a629?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_vcanonc",
                "organizer_name": "Víctor Canon",
            },
            {
                "name": "Torneo Interfacultades de Voleibol Mixto",
                "description": "Competencia deportiva amistosa en canchas múltiples. Ven con tu equipo o intégrate a la selección de tu facultad.",
                "place": "Coliseo de Deportes (Edif. 408)",
                "latitude": 4.636200,
                "longitude": -74.085500,
                "datetime_start": dt(84),
                "datetime_end": dt(90),
                "status": "PRO",
                "category": "DEP",
                "published": True,
                "image": "https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=800&q=80",
                "organizer_id": "mock_uid_dbarragang",
                "organizer_name": "Daniel Barragán",
            },
        ]

        count = 0
        for data in mock_events:
            name = data.pop("name")
            event, created = Event.objects.update_or_create(
                name=name,
                defaults=data
            )
            count += 1
            action = "creado" if created else "actualizado"
            self.stdout.write(f"Evento '{name}' {action}.")

        self.stdout.write(self.style.SUCCESS(f"Se procesaron {count} eventos mock exitosamente."))

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
        u_count = 0
        for data in mock_users:
            u_data = dict(data)
            email = u_data.pop("email")
            user, created = User.objects.update_or_create(email=email, defaults=u_data)
            user.set_password("Admin123*")
            user.save(update_fields=["password"])
            u_count += 1
            action = "creado" if created else "actualizado"
            self.stdout.write(f"Usuario {email} ({user.role}) {action}.")

        self.stdout.write(self.style.SUCCESS(f"Se procesaron {u_count} usuarios en events_service exitosamente."))
