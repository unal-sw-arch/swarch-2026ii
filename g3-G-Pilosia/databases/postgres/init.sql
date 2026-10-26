-- Base de datos de courses-service (asignaturas, grupos, horarios e inscripciones)

CREATE TABLE professors (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL
);

CREATE TABLE students (
    code        VARCHAR(10) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    program     VARCHAR(100) NOT NULL,
    max_credits INTEGER NOT NULL DEFAULT 20
);

CREATE TABLE courses (
    code    VARCHAR(10) PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    credits INTEGER NOT NULL CHECK (credits > 0)
);

CREATE TABLE course_groups (
    id           SERIAL PRIMARY KEY,
    course_code  VARCHAR(10) NOT NULL REFERENCES courses(code),
    group_number INTEGER NOT NULL,
    period       VARCHAR(6) NOT NULL,
    professor_id INTEGER NOT NULL REFERENCES professors(id),
    capacity     INTEGER NOT NULL CHECK (capacity > 0),
    UNIQUE (course_code, group_number, period)
);

-- day: 1 = lunes ... 6 = sábado
CREATE TABLE group_schedules (
    id         SERIAL PRIMARY KEY,
    group_id   INTEGER NOT NULL REFERENCES course_groups(id) ON DELETE CASCADE,
    day        INTEGER NOT NULL CHECK (day BETWEEN 1 AND 6),
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,
    CHECK (start_time < end_time)
);

CREATE TABLE enrollments (
    id           SERIAL PRIMARY KEY,
    student_code VARCHAR(10) NOT NULL REFERENCES students(code),
    group_id     INTEGER NOT NULL REFERENCES course_groups(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (student_code, group_id)
);

-- ---------------------------------------------------------------
-- Datos de prueba
-- ---------------------------------------------------------------

INSERT INTO professors (id, name, department) VALUES
(1, 'María Fernanda Castro', 'Ingeniería de Sistemas e Industrial'),
(2, 'Jorge Enrique Ramírez', 'Ingeniería de Sistemas e Industrial'),
(3, 'Paula Andrea Herrera', 'Física'),
(4, 'Ricardo Molina Suárez', 'Matemáticas');
SELECT setval('professors_id_seq', 4);

INSERT INTO students (code, name, program, max_credits) VALUES
('20231001', 'Laura Gómez Ruiz', 'Ingeniería de Sistemas y Computación', 20),
('20231002', 'Andrés Martínez López', 'Ingeniería de Sistemas y Computación', 20),
('20222003', 'Camila Torres Díaz', 'Ingeniería Industrial', 20),
('20241004', 'Santiago Rojas Pérez', 'Ingeniería de Sistemas y Computación', 20);

INSERT INTO courses (code, name, credits) VALUES
('MAT101', 'Cálculo Diferencial', 4),
('MAT102', 'Cálculo Integral', 4),
('MAT201', 'Álgebra Lineal', 3),
('FIS101', 'Física Mecánica', 4),
('INF101', 'Programación de Computadores', 3),
('INF201', 'Estructuras de Datos', 3),
('INF301', 'Bases de Datos', 3),
('INF401', 'Ingeniería de Software', 3),
('INF402', 'Arquitectura de Software', 3),
('HUM101', 'Cátedra de Ciudadanía', 2);

-- Grupos de periodos anteriores (se usan para la historia académica)
INSERT INTO course_groups (id, course_code, group_number, period, professor_id, capacity) VALUES
(1, 'INF101', 1, '2024-2', 1, 30),
(2, 'MAT101', 1, '2024-2', 4, 35),
(3, 'HUM101', 1, '2024-2', 2, 40),
(4, 'INF201', 1, '2025-1', 1, 30),
(5, 'MAT201', 1, '2025-1', 4, 35),
(6, 'MAT102', 1, '2025-1', 4, 35),
(7, 'FIS101', 1, '2025-1', 3, 30);

-- Grupos del periodo actual (2025-2)
INSERT INTO course_groups (id, course_code, group_number, period, professor_id, capacity) VALUES
(10, 'INF402', 1, '2025-2', 1, 30),
(11, 'INF402', 2, '2025-2', 2, 25),
(12, 'INF301', 1, '2025-2', 2, 30),
(13, 'INF301', 2, '2025-2', 1, 2),   -- grupo con cupo lleno, sirve para probar la validación
(14, 'INF401', 1, '2025-2', 2, 30),
(15, 'MAT201', 1, '2025-2', 4, 35),
(16, 'MAT102', 1, '2025-2', 4, 35),
(17, 'FIS101', 1, '2025-2', 3, 30),
(18, 'HUM101', 1, '2025-2', 2, 40),
(19, 'INF201', 1, '2025-2', 2, 30),
(20, 'MAT101', 1, '2025-2', 4, 35),
(21, 'INF101', 1, '2025-2', 2, 30);
SELECT setval('course_groups_id_seq', 21);

INSERT INTO group_schedules (group_id, day, start_time, end_time) VALUES
(10, 1, '07:00', '09:00'), (10, 3, '07:00', '09:00'),
(11, 2, '14:00', '16:00'), (11, 4, '14:00', '16:00'),
(12, 1, '09:00', '11:00'), (12, 3, '09:00', '11:00'),
(13, 2, '07:00', '09:00'), (13, 4, '07:00', '09:00'),
(14, 2, '09:00', '11:00'), (14, 4, '09:00', '11:00'),
(15, 1, '11:00', '13:00'), (15, 3, '11:00', '13:00'),
(16, 2, '11:00', '13:00'), (16, 5, '11:00', '13:00'),
(17, 1, '14:00', '16:00'), (17, 3, '14:00', '16:00'),
(18, 6, '08:00', '11:00'),
(19, 1, '07:00', '09:00'), (19, 5, '07:00', '09:00'),  -- se cruza con INF402 grupo 1
(20, 2, '16:00', '18:00'), (20, 4, '16:00', '18:00'),
(21, 5, '14:00', '17:00');

-- Inscripciones de periodos anteriores
INSERT INTO enrollments (student_code, group_id) VALUES
('20231001', 1), ('20231001', 2), ('20231001', 3),
('20231001', 4), ('20231001', 6), ('20231001', 7),
('20231002', 4), ('20231002', 5), ('20231002', 6),
('20222003', 1), ('20222003', 2);

-- Inscripciones del periodo actual
INSERT INTO enrollments (student_code, group_id) VALUES
('20231001', 10), ('20231001', 14),
('20231002', 10), ('20231002', 13),
('20222003', 13), ('20222003', 15),
('20241004', 15);
