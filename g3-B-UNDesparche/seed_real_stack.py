"""
Script de sembrado para la base de datos real en Docker (PostgreSQL)
Puebla users_service, events_service e inventory_service vía docker compose exec.
"""
import subprocess
import sys

USERS_CODE = """
from users.models import User

mock_users = [
    {
        'email': 'dbarragang@unal.edu.co',
        'name': 'Deibyd Barragán',
        'faculty': 'ING',
        'status': 'ACT',
        'role': 'SYS_ADMIN',
        'firebase_uid': 'rQhOsctzA4YV6zMll9P9KnRxt513',
        'is_staff': True,
        'is_superuser': True,
        'is_active': True,
    },
    {
        'email': 'jalzateco@unal.edu.co',
        'name': 'Julián Alzate',
        'faculty': 'ING',
        'status': 'ACT',
        'role': 'IMPLEMENT_ADMIN',
        'firebase_uid': 'mock_uid_jalzateco',
        'is_staff': True,
        'is_superuser': False,
        'is_active': True,
    },
    {
        'email': 'vcanonc@unal.edu.co',
        'name': 'Víctor Canon',
        'faculty': 'ING',
        'status': 'ACT',
        'role': 'EVENT_ADMIN',
        'firebase_uid': 'mock_uid_vcanonc',
        'is_staff': True,
        'is_superuser': False,
        'is_active': True,
    },
    {
        'email': 'dvelasquezg@unal.edu.co',
        'name': 'David Velásquez',
        'faculty': 'ING',
        'status': 'ACT',
        'role': 'COMMUNITY',
        'firebase_uid': 'mock_uid_dvelasquezg',
        'is_staff': False,
        'is_superuser': False,
        'is_active': True,
    },
    {
        'email': 'smorales@unal.edu.co',
        'name': 'Santiago Morales',
        'faculty': 'MED',
        'status': 'SAN',
        'role': 'COMMUNITY',
        'firebase_uid': 'mock_uid_smorales',
        'is_staff': False,
        'is_superuser': False,
        'is_active': True,
    },
    {
        'email': 'admin@unal.edu.co',
        'name': 'Admin Prueba',
        'faculty': 'ING',
        'status': 'ACT',
        'role': 'SYS_ADMIN',
        'firebase_uid': 'admin-uid',
        'is_staff': True,
        'is_superuser': True,
        'is_active': True,
    },
]

for u in mock_users:
    email = u.pop('email')
    user, _ = User.objects.update_or_create(email=email, defaults=u)
    user.set_password('Admin123*')
    user.save()
print(f'-> [Users] {len(mock_users)} usuarios sembrados correctamente.')
"""

EVENTS_CODE = """
from datetime import timedelta
from django.utils import timezone
from events.models import Event

now = timezone.now()
mock_events = [
    {
        'name': 'Torneo Relámpago de Microfútbol UNAL',
        'description': 'Torneo interfacultades de microfútbol masculino y femenino. Inscripciones abiertas en las canchas de deportes.',
        'place': 'Canchas Sintéticas de Deportes',
        'latitude': 4.636800,
        'longitude': -74.085000,
        'datetime_start': now + timedelta(hours=2),
        'datetime_end': now + timedelta(hours=6),
        'status': 'PRO',
        'category': 'DEP',
        'published': True,
        'image': 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=800&q=80',
        'organizer_id': 'mock_uid_vcanonc',
        'organizer_email': 'vcanonc@unal.edu.co',
    },
    {
        'name': 'Cineforo Freud: Ciclo Cine Latinoamericano',
        'description': 'Proyección y conversatorio abierto sobre cine latinoamericano contemporáneo.',
        'place': 'Auditorio Alfonso López Pumarejo (Edif. 401)',
        'latitude': 4.637500,
        'longitude': -74.083200,
        'datetime_start': now - timedelta(hours=1),
        'datetime_end': now + timedelta(hours=3),
        'status': 'ECU',
        'category': 'CUL',
        'published': True,
        'image': 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=800&q=80',
        'organizer_id': 'mock_uid_vcanonc',
        'organizer_email': 'vcanonc@unal.edu.co',
    },
    {
        'name': 'Taller de Robótica Competitiva y Drones',
        'description': 'Borrador de actividad preparatoria para el torneo nacional de robótica. Solo organizadores.',
        'place': 'Laboratorio de Mecatrónica (Edif. 411)',
        'latitude': 4.639800,
        'longitude': -74.083000,
        'datetime_start': now + timedelta(hours=100),
        'datetime_end': now + timedelta(hours=104),
        'status': 'PRO',
        'category': 'ACA',
        'published': False,
        'image': None,
        'organizer_id': 'mock_uid_vcanonc',
        'organizer_email': 'vcanonc@unal.edu.co',
    },
    {
        'name': 'Torneo Interfacultades de Voleibol Mixto',
        'description': 'Competencia deportiva en coliseo principal.',
        'place': 'Coliseo de Deportes (Edif. 408)',
        'latitude': 4.636200,
        'longitude': -74.085500,
        'datetime_start': now + timedelta(hours=84),
        'datetime_end': now + timedelta(hours=90),
        'status': 'PRO',
        'category': 'DEP',
        'published': True,
        'image': 'https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=800&q=80',
        'organizer_id': 'mock_uid_dbarragang',
        'organizer_email': 'dbarragang@unal.edu.co',
    },
]

for ev in mock_events:
    name = ev.pop('name')
    Event.objects.update_or_create(name=name, defaults=ev)
print(f'-> [Events] {len(mock_events)} eventos sembrados correctamente.')
"""

INVENTORY_CODE = """
from datetime import timedelta
from django.utils import timezone
from inventory.models import Implement, Reserve, Borrowing

# Limpiar reservas y préstamos previos para evitar bloqueos por límite de reservas activas
Reserve.objects.all().delete()
Borrowing.objects.all().delete()

now = timezone.now()
mock_implements = [
    {
        'name': 'Balón de Baloncesto Spalding TF-1000 #7',
        'description': 'Balón de cuero sintético para cancha múltiple exterior o coliseo.',
        'category': 'BAL',
        'state': 'DIS',
        'faculty': 'ING',
        'image': 'https://images.unsplash.com/photo-1519861531473-9200262188bf?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Set de Raquetas y Pelotas de Tenis de Mesa',
        'description': 'Incluye 2 raquetas de control con goma lisa y 3 pelotas Donic 3 estrellas.',
        'category': 'RAQ',
        'state': 'DIS',
        'faculty': 'ING',
        'image': 'https://images.unsplash.com/photo-1534158914592-062992fbe900?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Mesa Plegable Profesional de Ping Pong',
        'description': 'Mesa reglamentaria de 25mm con ruedas de traslado y red tensora instalada en el hall.',
        'category': 'MES',
        'state': 'RES',
        'faculty': 'ING',
        'image': 'https://images.unsplash.com/photo-1609710228159-0fa9bd7c0827?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Ajedrez de Madera Profesional Staunton #5',
        'description': 'Tablero enrollable de vinilo pesado y piezas de madera con doble dama para competición.',
        'category': 'JUM',
        'state': 'DIS',
        'faculty': 'CCS',
        'image': 'https://images.unsplash.com/photo-1529699211952-734e80c4d42b?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Balón de Voleibol Mikasa MVA200 Profesional',
        'description': 'Diseño en espiral de 8 paneles para vóley sala o prado. Préstamo por 2 horas máximo.',
        'category': 'BAL',
        'state': 'PRE',
        'faculty': 'MED',
        'image': 'https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Juego de Mesa Catán Edición Especial',
        'description': 'Juego completo de colonos con tablero modular de hexágonos, cartas y dados intactos.',
        'category': 'JUM',
        'state': 'DIS',
        'faculty': 'CHS',
        'image': 'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Set de Frisbees Ultimate Discraft 175g',
        'description': 'Discos oficiales de competición para entrenamiento en la zona de canchas.',
        'category': 'JUR',
        'state': 'DIS',
        'faculty': 'ART',
        'image': None,
    },
    {
        'name': 'Balón de Fútbol #5 Golty Origen Balón de Oro',
        'description': 'Balón cosido a mano para grama natural o sintética. En mantenimiento por costura.',
        'category': 'BAL',
        'state': 'NDS',
        'faculty': 'ING',
        'image': 'https://images.unsplash.com/photo-1579952363873-27f3bade9f55?auto=format&fit=crop&w=600&q=80',
    },
    {
        'name': 'Cronómetro Digital Profesional con Memoria',
        'description': 'Ideal para pruebas de atletismo o toma de tiempos en eventos deportivos de salud.',
        'category': 'OTS',
        'state': 'DIS',
        'faculty': 'ENF',
        'image': None,
    },
]

for imp in mock_implements:
    name = imp.pop('name')
    Implement.objects.update_or_create(name=name, defaults=imp)
print(f'-> [Inventory] {len(mock_implements)} implementos sembrados correctamente.')
"""

def run_seed():
    print("Iniciando sembrado de datos en los contenedores PostgreSQL...")
    
    # 1. Users
    print("\n1. Sembrando users_service...")
    subprocess.run(
        ["docker", "compose", "exec", "-T", "users", "python", "manage.py", "shell", "-c", USERS_CODE],
        check=True
    )
    
    # 2. Events
    print("\n2. Sembrando events_service...")
    subprocess.run(
        ["docker", "compose", "exec", "-T", "events", "python", "manage.py", "shell", "-c", EVENTS_CODE],
        check=True
    )
    
    # 3. Inventory
    print("\n3. Sembrando inventory_service...")
    subprocess.run(
        ["docker", "compose", "exec", "-T", "inventory", "python", "manage.py", "shell", "-c", INVENTORY_CODE],
        check=True
    )
    
    print("\n¡Sembrado exitoso de todos los microservicios en vivo!")

if __name__ == "__main__":
    run_seed()
