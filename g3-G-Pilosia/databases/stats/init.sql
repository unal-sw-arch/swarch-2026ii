-- Base de datos de stats-service (estadísticas de inscripción armadas con eventos)

-- Cada evento que llega por RabbitMQ queda registrado
CREATE TABLE enrollment_events (
    id           SERIAL PRIMARY KEY,
    event_type   VARCHAR(30) NOT NULL,
    student_code VARCHAR(10) NOT NULL,
    group_id     INTEGER NOT NULL,
    course_code  VARCHAR(10) NOT NULL,
    received_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Resumen por asignatura, se actualiza con cada evento
CREATE TABLE course_stats (
    course_code   VARCHAR(10) PRIMARY KEY,
    course_name   VARCHAR(100) NOT NULL,
    enrolled      INTEGER NOT NULL DEFAULT 0,
    cancelled     INTEGER NOT NULL DEFAULT 0,
    last_event_at TIMESTAMP NOT NULL DEFAULT NOW()
);
