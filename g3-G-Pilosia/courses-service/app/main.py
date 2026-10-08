"""
courses-service: API REST de Pilosia.

Maneja estudiantes, profesores, asignaturas, grupos con su horario e inscripciones.
Guarda todo en PostgreSQL.
"""
import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.db import get_connection

CURRENT_PERIOD = os.getenv("CURRENT_PERIOD", "2025-2")

DAY_NAMES = {1: "Lunes", 2: "Martes", 3: "Miércoles", 4: "Jueves", 5: "Viernes", 6: "Sábado"}

app = FastAPI(title="Pilosia - courses-service", version="1.0")


class EnrollmentRequest(BaseModel):
    group_id: int


# ---------------------------------------------------------------
# Funciones de apoyo
# ---------------------------------------------------------------

def format_block(row):
    return {
        "day": row["day"],
        "day_name": DAY_NAMES[row["day"]],
        "start": row["start_time"].strftime("%H:%M"),
        "end": row["end_time"].strftime("%H:%M"),
    }


def get_schedules(conn, group_ids):
    """Devuelve un diccionario {group_id: [bloques de horario]}."""
    schedules = {group_id: [] for group_id in group_ids}
    if not group_ids:
        return schedules
    rows = conn.execute(
        """SELECT group_id, day, start_time, end_time FROM group_schedules
           WHERE group_id = ANY(%s) ORDER BY day, start_time""",
        (list(group_ids),),
    ).fetchall()
    for row in rows:
        schedules[row["group_id"]].append(format_block(row))
    return schedules


def find_student(conn, code):
    student = conn.execute("SELECT * FROM students WHERE code = %s", (code,)).fetchone()
    if student is None:
        raise HTTPException(status_code=404, detail="Estudiante no encontrado")
    return student


def current_credits(conn, student_code):
    row = conn.execute(
        """SELECT COALESCE(SUM(c.credits), 0) AS total
           FROM enrollments e
           JOIN course_groups g ON g.id = e.group_id
           JOIN courses c ON c.code = g.course_code
           WHERE e.student_code = %s AND g.period = %s""",
        (student_code, CURRENT_PERIOD),
    ).fetchone()
    return row["total"]


# ---------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------

@app.get("/api/health")
def health():
    return {"status": "ok", "period": CURRENT_PERIOD}


@app.get("/api/students")
def list_students():
    with get_connection() as conn:
        return conn.execute("SELECT * FROM students ORDER BY name").fetchall()


@app.get("/api/students/{code}")
def get_student(code: str):
    with get_connection() as conn:
        return find_student(conn, code)


@app.get("/api/professors")
def list_professors():
    with get_connection() as conn:
        return conn.execute("SELECT * FROM professors ORDER BY name").fetchall()


@app.get("/api/courses")
def list_courses(search: str = ""):
    """Catálogo del periodo actual: cada asignatura con sus grupos, cupos y horario."""
    with get_connection() as conn:
        rows = conn.execute(
            """SELECT c.code, c.name, c.credits,
                      g.id AS group_id, g.group_number, g.capacity,
                      p.name AS professor,
                      (SELECT COUNT(*) FROM enrollments e WHERE e.group_id = g.id) AS enrolled
               FROM courses c
               JOIN course_groups g ON g.course_code = c.code AND g.period = %s
               JOIN professors p ON p.id = g.professor_id
               WHERE c.name ILIKE %s OR c.code ILIKE %s
               ORDER BY c.name, g.group_number""",
            (CURRENT_PERIOD, f"%{search}%", f"%{search}%"),
        ).fetchall()
        schedules = get_schedules(conn, [r["group_id"] for r in rows])

    courses = {}
    for r in rows:
        course = courses.setdefault(
            r["code"], {"code": r["code"], "name": r["name"], "credits": r["credits"], "groups": []}
        )
        course["groups"].append({
            "id": r["group_id"],
            "number": r["group_number"],
            "professor": r["professor"],
            "capacity": r["capacity"],
            "enrolled": r["enrolled"],
            "available": r["capacity"] - r["enrolled"],
            "schedule": schedules[r["group_id"]],
        })
    return {"period": CURRENT_PERIOD, "courses": list(courses.values())}


@app.get("/api/students/{code}/enrollments")
def list_enrollments(code: str):
    """Asignaturas que el estudiante tiene inscritas en el periodo actual."""
    with get_connection() as conn:
        student = find_student(conn, code)
        rows = conn.execute(
            """SELECT e.id, g.id AS group_id, g.group_number, c.code, c.name, c.credits,
                      p.name AS professor
               FROM enrollments e
               JOIN course_groups g ON g.id = e.group_id
               JOIN courses c ON c.code = g.course_code
               JOIN professors p ON p.id = g.professor_id
               WHERE e.student_code = %s AND g.period = %s
               ORDER BY c.name""",
            (code, CURRENT_PERIOD),
        ).fetchall()
        schedules = get_schedules(conn, [r["group_id"] for r in rows])

    enrollments = [
        {
            "id": r["id"],
            "group_id": r["group_id"],
            "group_number": r["group_number"],
            "course_code": r["code"],
            "course_name": r["name"],
            "credits": r["credits"],
            "professor": r["professor"],
            "schedule": schedules[r["group_id"]],
        }
        for r in rows
    ]
    return {
        "period": CURRENT_PERIOD,
        "max_credits": student["max_credits"],
        "total_credits": sum(e["credits"] for e in enrollments),
        "enrollments": enrollments,
    }


@app.post("/api/students/{code}/enrollments", status_code=201)
def create_enrollment(code: str, body: EnrollmentRequest):
    """Inscribe al estudiante en un grupo, revisando las reglas de inscripción."""
    with get_connection() as conn:
        student = find_student(conn, code)

        # FOR UPDATE bloquea el grupo mientras se revisan los cupos,
        # así dos inscripciones al mismo tiempo no pasan del cupo.
        group = conn.execute(
            """SELECT g.*, c.name AS course_name, c.credits
               FROM course_groups g JOIN courses c ON c.code = g.course_code
               WHERE g.id = %s FOR UPDATE OF g""",
            (body.group_id,),
        ).fetchone()
        if group is None:
            raise HTTPException(status_code=404, detail="Grupo no encontrado")
        if group["period"] != CURRENT_PERIOD:
            raise HTTPException(status_code=400, detail="El grupo no es del periodo actual")

        enrolled = conn.execute(
            "SELECT COUNT(*) AS total FROM enrollments WHERE group_id = %s", (group["id"],)
        ).fetchone()["total"]
        if enrolled >= group["capacity"]:
            raise HTTPException(status_code=409, detail="El grupo no tiene cupos disponibles")

        same_course = conn.execute(
            """SELECT 1 FROM enrollments e JOIN course_groups g ON g.id = e.group_id
               WHERE e.student_code = %s AND g.course_code = %s AND g.period = %s""",
            (code, group["course_code"], CURRENT_PERIOD),
        ).fetchone()
        if same_course:
            raise HTTPException(status_code=409, detail="Ya tienes inscrita esta asignatura")

        if current_credits(conn, code) + group["credits"] > student["max_credits"]:
            raise HTTPException(
                status_code=400,
                detail=f"Superas el límite de {student['max_credits']} créditos del periodo",
            )

        # Cruce de horario: mismo día y las horas se solapan
        conflict = conn.execute(
            """SELECT c.name FROM group_schedules nuevo
               JOIN group_schedules actual ON actual.day = nuevo.day
                    AND actual.start_time < nuevo.end_time
                    AND nuevo.start_time < actual.end_time
               JOIN enrollments e ON e.group_id = actual.group_id AND e.student_code = %s
               JOIN course_groups g ON g.id = e.group_id AND g.period = %s
               JOIN courses c ON c.code = g.course_code
               WHERE nuevo.group_id = %s
               LIMIT 1""",
            (code, CURRENT_PERIOD, group["id"]),
        ).fetchone()
        if conflict:
            raise HTTPException(
                status_code=409, detail=f"Hay cruce de horario con {conflict['name']}"
            )

        new_id = conn.execute(
            "INSERT INTO enrollments (student_code, group_id) VALUES (%s, %s) RETURNING id",
            (code, group["id"]),
        ).fetchone()["id"]

    return {
        "id": new_id,
        "message": f"Inscribiste {group['course_name']} (grupo {group['group_number']})",
    }


@app.delete("/api/students/{code}/enrollments/{enrollment_id}")
def delete_enrollment(code: str, enrollment_id: int):
    """Cancela una inscripción del periodo actual."""
    with get_connection() as conn:
        deleted = conn.execute(
            """DELETE FROM enrollments e USING course_groups g
               WHERE e.id = %s AND e.student_code = %s
                 AND g.id = e.group_id AND g.period = %s
               RETURNING e.id""",
            (enrollment_id, code, CURRENT_PERIOD),
        ).fetchone()
    if deleted is None:
        raise HTTPException(status_code=404, detail="Inscripción no encontrada")
    return {"message": "Inscripción cancelada"}


@app.get("/api/students/{code}/schedule")
def get_student_schedule(code: str):
    """Bloques de horario de las asignaturas inscritas, para armar 'Mi horario'."""
    with get_connection() as conn:
        find_student(conn, code)
        rows = conn.execute(
            """SELECT s.day, s.start_time, s.end_time, c.code, c.name, g.group_number
               FROM enrollments e
               JOIN course_groups g ON g.id = e.group_id
               JOIN courses c ON c.code = g.course_code
               JOIN group_schedules s ON s.group_id = g.id
               WHERE e.student_code = %s AND g.period = %s
               ORDER BY s.day, s.start_time""",
            (code, CURRENT_PERIOD),
        ).fetchall()

    blocks = [
        {**format_block(r), "course_code": r["code"], "course_name": r["name"],
         "group_number": r["group_number"]}
        for r in rows
    ]
    return {"period": CURRENT_PERIOD, "blocks": blocks}


@app.get("/api/professors/{professor_id}/groups")
def list_professor_groups(professor_id: int):
    """Grupos que dicta el profesor en el periodo actual."""
    with get_connection() as conn:
        professor = conn.execute(
            "SELECT * FROM professors WHERE id = %s", (professor_id,)
        ).fetchone()
        if professor is None:
            raise HTTPException(status_code=404, detail="Profesor no encontrado")
        rows = conn.execute(
            """SELECT g.id, g.group_number, g.capacity, c.code, c.name, c.credits,
                      (SELECT COUNT(*) FROM enrollments e WHERE e.group_id = g.id) AS enrolled
               FROM course_groups g JOIN courses c ON c.code = g.course_code
               WHERE g.professor_id = %s AND g.period = %s
               ORDER BY c.name, g.group_number""",
            (professor_id, CURRENT_PERIOD),
        ).fetchall()
        schedules = get_schedules(conn, [r["id"] for r in rows])

    groups = [
        {
            "id": r["id"],
            "number": r["group_number"],
            "course_code": r["code"],
            "course_name": r["name"],
            "credits": r["credits"],
            "capacity": r["capacity"],
            "enrolled": r["enrolled"],
            "schedule": schedules[r["id"]],
        }
        for r in rows
    ]
    return {"period": CURRENT_PERIOD, "professor": professor, "groups": groups}


@app.get("/api/groups/{group_id}/students")
def list_group_students(group_id: int):
    """Datos de un grupo y sus estudiantes inscritos.
    Lo usa el profesor para calificar y grades-service para validar una nota."""
    with get_connection() as conn:
        group = conn.execute(
            """SELECT g.id, g.group_number, g.period, c.code, c.name, c.credits,
                      p.id AS professor_id, p.name AS professor_name
               FROM course_groups g
               JOIN courses c ON c.code = g.course_code
               JOIN professors p ON p.id = g.professor_id
               WHERE g.id = %s""",
            (group_id,),
        ).fetchone()
        if group is None:
            raise HTTPException(status_code=404, detail="Grupo no encontrado")
        students = conn.execute(
            """SELECT s.code, s.name, s.program
               FROM enrollments e JOIN students s ON s.code = e.student_code
               WHERE e.group_id = %s ORDER BY s.name""",
            (group_id,),
        ).fetchall()

    return {
        "id": group["id"],
        "number": group["group_number"],
        "period": group["period"],
        "course": {"code": group["code"], "name": group["name"], "credits": group["credits"]},
        "professor": {"id": group["professor_id"], "name": group["professor_name"]},
        "students": students,
    }
