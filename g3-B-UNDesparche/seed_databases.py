"""
Script de inicialización y sembrado de datos mock para UNDesparche.
Puebla SQLite directamente tanto para users_service como para events_service.
"""
import sqlite3
import sys
from datetime import datetime, timedelta, timezone

def seed_all():
    now = datetime.now(timezone.utc)

    # 1. Seed users_service
    print("[1/2] Sembrando usuarios en users_service/db.sqlite3...")
    users_conn = sqlite3.connect('users_service/db.sqlite3')
    u_cur = users_conn.cursor()

    mock_users = [
        {
            'email': 'dbarragang@unal.edu.co',
            'name': 'Daniel Barragán',
            'faculty': 'ING',
            'status': 'ACT',
            'role': 'SYS_ADMIN',
            'firebase_uid': 'mock_uid_dbarragang',
            'is_staff': 1,
            'is_superuser': 1,
            'is_active': 1,
            'date_joined': '2024-02-15 15:00:00',
        },
        {
            'email': 'jalzateco@unal.edu.co',
            'name': 'Julián Alzate',
            'faculty': 'ING',
            'status': 'ACT',
            'role': 'IMPLEMENT_ADMIN',
            'firebase_uid': 'mock_uid_jalzateco',
            'is_staff': 1,
            'is_superuser': 0,
            'is_active': 1,
            'date_joined': '2024-03-10 14:15:00',
        },
        {
            'email': 'vcanonc@unal.edu.co',
            'name': 'Víctor Canon',
            'faculty': 'ING',
            'status': 'ACT',
            'role': 'EVENT_ADMIN',
            'firebase_uid': 'mock_uid_vcanonc',
            'is_staff': 1,
            'is_superuser': 0,
            'is_active': 1,
            'date_joined': '2024-03-01 19:30:00',
        },
        {
            'email': 'dvelasquezg@unal.edu.co',
            'name': 'David Velásquez',
            'faculty': 'ING',
            'status': 'ACT',
            'role': 'COMMUNITY',
            'firebase_uid': 'mock_uid_dvelasquezg',
            'is_staff': 0,
            'is_superuser': 0,
            'is_active': 1,
            'date_joined': '2024-04-05 21:00:00',
        },
        {
            'email': 'smorales@unal.edu.co',
            'name': 'Santiago Morales',
            'faculty': 'MED',
            'status': 'SAN',
            'role': 'COMMUNITY',
            'firebase_uid': 'mock_uid_smorales',
            'is_staff': 0,
            'is_superuser': 0,
            'is_active': 1,
            'date_joined': '2024-04-12 16:45:00',
        },
    ]

    for u in mock_users:
        u_cur.execute('SELECT id FROM users_user WHERE email = ?', (u['email'],))
        row = u_cur.fetchone()
        if row:
            u_cur.execute('''
                UPDATE users_user 
                SET name=?, faculty=?, status=?, role=?, firebase_uid=?, is_staff=?, is_active=?
                WHERE email=?
            ''', (u['name'], u['faculty'], u['status'], u['role'], u['firebase_uid'], u['is_staff'], u['is_active'], u['email']))
        else:
            u_cur.execute('''
                INSERT INTO users_user 
                (password, last_login, is_superuser, is_staff, is_active, date_joined, email, name, faculty, status, role, firebase_uid)
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', ('!', u['is_superuser'], u['is_staff'], u['is_active'], u['date_joined'], u['email'], u['name'], u['faculty'], u['status'], u['role'], u['firebase_uid']))

    users_conn.commit()
    user_count = u_cur.execute('SELECT count(*) FROM users_user').fetchone()[0]
    print(f"-> Usuarios totales en BD: {user_count}")
    users_conn.close()

    # 2. Seed events_service
    print("[2/2] Sembrando eventos en events_service/db.sqlite3...")
    events_conn = sqlite3.connect('events_service/db.sqlite3')
    e_cur = events_conn.cursor()

    def dt_str(hours_delta):
        return (now + timedelta(hours=hours_delta)).strftime('%Y-%m-%d %H:%M:%S')

    mock_events = [
        {
            'name': 'Torneo Relámpago de Microfútbol UNAL',
            'description': 'Torneo interfacultades de microfútbol masculino y femenino. Inscripciones abiertas en las canchas de deportes. Trae tu carné estudiantil.',
            'place': 'Canchas Sintéticas de Deportes',
            'latitude': 4.636800,
            'longitude': -74.085000,
            'datetime_start': dt_str(2),
            'datetime_end': dt_str(6),
            'status': 'PRO',
            'category': 'DEP',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Cineforo Freud: Ciclo Cine Latinoamericano',
            'description': 'Proyección y conversatorio abierto sobre cine latinoamericano contemporáneo. Palomitas gratis para los primeros 50 asistentes.',
            'place': 'Auditorio Alfonso López Pumarejo (Edif. 401)',
            'latitude': 4.637500,
            'longitude': -74.083200,
            'datetime_start': dt_str(-1),
            'datetime_end': dt_str(3),
            'status': 'ECU',
            'category': 'CUL',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Asamblea General Estudiantil Sede Bogotá',
            'description': 'Espacio de discusión triestamentaria sobre bienestar universitario, presupuesto y reformas académicas. Asistencia de todas las facultades.',
            'place': 'Plaza Che Guevara / Auditorio León de Greiff',
            'latitude': 4.638500,
            'longitude': -74.084100,
            'datetime_start': dt_str(24),
            'datetime_end': dt_str(28),
            'status': 'PRO',
            'category': 'ASA',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_dbarragang',
            'organizer_name': 'Daniel Barragán',
        },
        {
            'name': 'Hackathon de Software Libre y Datos Abiertos',
            'description': '48 horas de desarrollo colaborativo para crear soluciones a problemáticas del campus. Mentorías con egresados de Ingeniería de Sistemas.',
            'place': 'Edificio 453 - Aulas de Ingeniería (Salón 201)',
            'latitude': 4.639100,
            'longitude': -74.082700,
            'datetime_start': dt_str(48),
            'datetime_end': dt_str(72),
            'status': 'PRO',
            'category': 'ACA',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_dbarragang',
            'organizer_name': 'Daniel Barragán',
        },
        {
            'name': 'Tarde de Juegos de Mesa y Desparche Recreativo',
            'description': 'Ven a desconectarte entre parciales. Tenemos Catán, Carcassonne, dominó y jenga. Trae tus amigos o únete a una mesa libre.',
            'place': 'Prados de la Hemeroteca Nacional',
            'latitude': 4.640200,
            'longitude': -74.086500,
            'datetime_start': dt_str(12),
            'datetime_end': dt_str(16),
            'status': 'PRO',
            'category': 'PAR',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Feria Universitaria del Libro y Arte Gráfico',
            'description': 'Muestra editorial independiente, fanzines, grabados e ilustraciones de estudiantes de la Facultad de Artes.',
            'place': 'Edificio 301 - Escuela de Bellas Artes',
            'latitude': 4.635900,
            'longitude': -74.084600,
            'datetime_start': dt_str(36),
            'datetime_end': dt_str(42),
            'status': 'PRO',
            'category': 'CUL',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Taller de Robótica Competitiva y Drones',
            'description': 'Borrador de actividad preparatoria para el torneo nacional de robótica. Solo organizadores y coordinadores de laboratorio.',
            'place': 'Laboratorio de Mecatrónica (Edif. 411)',
            'latitude': 4.639800,
            'longitude': -74.083000,
            'datetime_start': dt_str(100),
            'datetime_end': dt_str(104),
            'status': 'PRO',
            'category': 'ACA',
            'published': 0,
            'image': None,
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Concierto de Jazz y Ritmos Latinos al Aire Libre',
            'description': 'Presentación en vivo de ensambles estudiantiles del Conservatorio de Música. Entrada libre para toda la comunidad universitaria.',
            'place': 'Plazoleta Central / Concha Acústica',
            'latitude': 4.637200,
            'longitude': -74.083800,
            'datetime_start': dt_str(60),
            'datetime_end': dt_str(64),
            'status': 'PRO',
            'category': 'CUL',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1511192336575-5a79af67a629?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_vcanonc',
            'organizer_name': 'Víctor Canon',
        },
        {
            'name': 'Torneo Interfacultades de Voleibol Mixto',
            'description': 'Competencia deportiva amistosa en canchas múltiples. Ven con tu equipo o intégrate a la selección de tu facultad.',
            'place': 'Coliseo de Deportes (Edif. 408)',
            'latitude': 4.636200,
            'longitude': -74.085500,
            'datetime_start': dt_str(84),
            'datetime_end': dt_str(90),
            'status': 'PRO',
            'category': 'DEP',
            'published': 1,
            'image': 'https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=800&q=80',
            'organizer_id': 'mock_uid_dbarragang',
            'organizer_name': 'Daniel Barragán',
        },
    ]

    for ev in mock_events:
        e_cur.execute('SELECT id FROM events_event WHERE name = ?', (ev['name'],))
        row = e_cur.fetchone()
        if row:
            e_cur.execute('''
                UPDATE events_event
                SET description=?, place=?, latitude=?, longitude=?, datetime_start=?, datetime_end=?,
                    status=?, category=?, published=?, image=?, organizer_id=?, organizer_name=?
                WHERE name=?
            ''', (ev['description'], ev['place'], ev['latitude'], ev['longitude'], ev['datetime_start'], ev['datetime_end'],
                  ev['status'], ev['category'], ev['published'], ev['image'], ev['organizer_id'], ev['organizer_name'], ev['name']))
        else:
            e_cur.execute('''
                INSERT INTO events_event
                (name, description, place, latitude, longitude, datetime_start, datetime_end, status, category, published, image, organizer_id, organizer_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (ev['name'], ev['description'], ev['place'], ev['latitude'], ev['longitude'], ev['datetime_start'], ev['datetime_end'],
                  ev['status'], ev['category'], ev['published'], ev['image'], ev['organizer_id'], ev['organizer_name']))

    events_conn.commit()
    event_count = e_cur.execute('SELECT count(*) FROM events_event').fetchone()[0]
    print(f"-> Eventos totales en BD: {event_count}")
    events_conn.close()

    # 3. Seed inventory_service via manage.py seed_inventory
    print("[3/3] Sembrando inventario en inventory_service/db.sqlite3...")
    import subprocess
    subprocess.run([sys.executable, 'inventory_service/manage.py', 'seed_inventory'], check=True)

    print("¡Sembrado completado con éxito para los 3 microservicios!")

if __name__ == '__main__':
    seed_all()
