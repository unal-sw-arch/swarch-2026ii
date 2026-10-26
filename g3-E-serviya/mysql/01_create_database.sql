-- =========================================================
-- DATABASE: marketplace_services
-- MySQL 8+
-- =========================================================
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS marketplace_services
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE marketplace_services;

-- =========================================================
-- USERS & AUTH
-- =========================================================

CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name ENUM('CLIENT', 'OFFERER', 'ADMIN') NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id INT UNSIGNED NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_role UNIQUE (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id)
        ON DELETE CASCADE
);

CREATE TABLE consents (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    accepted BOOLEAN NOT NULL,
    consented_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_consents_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_prt_token_hash (token_hash),
    INDEX idx_prt_user (user_id),
    INDEX idx_prt_expires (expires_at),

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =========================================================
-- ADDRESSES
-- =========================================================

CREATE TABLE addresses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,

    -- AES-256-GCM encrypted
    address_line VARBINARY(1024) NOT NULL,

    city VARCHAR(150) NOT NULL,

    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_addresses_user (user_id),
    INDEX idx_addresses_geo (latitude, longitude),

    CONSTRAINT fk_addresses_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =========================================================
-- USER PROFILES
-- =========================================================

CREATE TABLE user_profiles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT UNSIGNED NOT NULL UNIQUE,

    full_name VARCHAR(255) NOT NULL,

    document_type VARCHAR(50) NOT NULL,

    -- AES-256-GCM encrypted
    document_number VARBINARY(512) NOT NULL,
    phone_number VARBINARY(512) NOT NULL,

    primary_address_id BIGINT UNSIGNED NULL,

    profile_photo_url VARCHAR(1000) NULL,

    bio TEXT NULL,

    profile_type ENUM('NATURAL', 'COMPANY') NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_profiles_primary_address
        FOREIGN KEY (primary_address_id)
        REFERENCES addresses(id)
        ON DELETE SET NULL
);

-- =========================================================
-- OFFERER
-- =========================================================

CREATE TABLE offerer_profiles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT UNSIGNED NOT NULL UNIQUE,

    whatsapp_number VARCHAR(30) NOT NULL,

    public_description TEXT NULL,

    specialty VARCHAR(255) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_offerer_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE offerer_availabilities (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    offerer_id BIGINT UNSIGNED NOT NULL,

    week_day TINYINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_offerer_availability_offerer (offerer_id),

    CONSTRAINT chk_offerer_week_day
        CHECK (week_day BETWEEN 0 AND 6),

    CONSTRAINT fk_offerer_availability_user
        FOREIGN KEY (offerer_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- =========================================================
-- CATEGORIES & SERVICES
-- =========================================================

CREATE TABLE categories (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE services (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    offerer_id BIGINT UNSIGNED NOT NULL,

    title VARCHAR(255) NOT NULL,

    description TEXT NOT NULL,

    photos JSON NULL,

    price_hourly DECIMAL(12,2) NOT NULL,

    category_id BIGINT UNSIGNED NOT NULL,

    average_duration_minutes INT UNSIGNED NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    operation_radius_km DECIMAL(8,2) NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    deleted_at DATETIME NULL,

    INDEX idx_services_category (category_id),
    INDEX idx_services_price (price_hourly),
    INDEX idx_services_offerer_category (offerer_id, category_id),
    INDEX idx_services_category_price (category_id, price_hourly),

    CONSTRAINT fk_services_offerer
        FOREIGN KEY (offerer_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_services_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
);

CREATE TABLE service_availabilities (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    service_id BIGINT UNSIGNED NOT NULL,

    week_day TINYINT UNSIGNED NOT NULL,

    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_service_availability_service (service_id),

    CONSTRAINT chk_service_week_day
        CHECK (week_day BETWEEN 0 AND 6),

    CONSTRAINT fk_service_availability_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE
);

-- =========================================================
-- SERVICE REQUESTS
-- =========================================================

CREATE TABLE service_requests (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    service_id BIGINT UNSIGNED NOT NULL,

    previous_request_id BIGINT UNSIGNED NULL UNIQUE,

    client_id BIGINT UNSIGNED NOT NULL,

    offerer_id BIGINT UNSIGNED NOT NULL,

    address_id BIGINT UNSIGNED NOT NULL,

    scheduled_date DATETIME NOT NULL,

    status ENUM(
        'PENDING',
        'ACCEPTED',
        'PRESUMABLY_COMPLETED',
        'REJECTED',
        'CANCELLED',
        'COMPLETED',
        'RESCHEDULED',
        'NOT_PROVIDED'
    ) NOT NULL DEFAULT 'PENDING',

    updated_by BIGINT UNSIGNED NULL,

    requested_price DECIMAL(12,2) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at DATETIME NULL,

    updated_status_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_service_requests_status (status),
    INDEX idx_service_requests_client_status (client_id, status),
    INDEX idx_service_requests_service_status (service_id, status),
    INDEX idx_service_requests_scheduled_date (scheduled_date),
    INDEX idx_service_requests_updated_status_at (updated_status_at),

    CONSTRAINT fk_service_requests_service
        FOREIGN KEY (service_id)
        REFERENCES services(id),

    CONSTRAINT fk_service_requests_previous
        FOREIGN KEY (previous_request_id)
        REFERENCES service_requests(id),

    CONSTRAINT fk_service_requests_client
        FOREIGN KEY (client_id)
        REFERENCES users(id),

    CONSTRAINT fk_service_requests_offerer
        FOREIGN KEY (offerer_id)
        REFERENCES users(id),

    CONSTRAINT fk_service_requests_address
        FOREIGN KEY (address_id)
        REFERENCES addresses(id),

    CONSTRAINT fk_service_requests_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id)
);

CREATE TABLE reschedule_proposals (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    request_id BIGINT UNSIGNED NOT NULL,

    -- Denormalizados desde la solicitud (inmutables por propuesta): permiten listar/filtrar
    -- por parte y ordenar por created_at con un solo indice, sin pasar por service_requests.
    client_id BIGINT UNSIGNED NOT NULL,
    offerer_id BIGINT UNSIGNED NOT NULL,

    reason TEXT NOT NULL,

    proposed_date DATETIME NOT NULL,

    status ENUM(
        'PENDING',
        'ACCEPTED',
        'REJECTED',
        'CANCELLED',
        'SUPERSEDED'
    ) NOT NULL DEFAULT 'PENDING',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    responded_at DATETIME NULL,

    INDEX idx_reschedule_request (request_id),
    -- Sirven al filtro por parte + ORDER BY created_at DESC de los listados (recibidas/enviadas).
    INDEX idx_reschedule_client_created (client_id, created_at),
    INDEX idx_reschedule_offerer_created (offerer_id, created_at),

    CONSTRAINT fk_reschedule_request
        FOREIGN KEY (request_id)
        REFERENCES service_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reschedule_client
        FOREIGN KEY (client_id)
        REFERENCES users(id),

CONSTRAINT fk_reschedule_offerer
        FOREIGN KEY (offerer_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    -- VIRTUAL (no STORED): MySQL rechaza (ERROR 1215) una columna generada STORED cuya columna base
    -- (request_id) tiene un FK ON DELETE CASCADE (ver fk_reschedule_request). VIRTUAL conserva el CASCADE
    -- y la UNIQUE (el índice único sobre columna virtual se materializa y se aplica igual). Fix del merge.
    pending_request_id BIGINT UNSIGNED AS (CASE WHEN status = 'PENDING' THEN request_id END) VIRTUAL,
    CONSTRAINT uq_reschedule_pending_per_request UNIQUE (pending_request_id)
);

-- =========================================================
-- SERVICE FEEDBACK (rating + reseña unificados)
-- =========================================================

-- Un feedback por solicitud: agrupa la calificación (opcional) y la reseña
-- (opcional) que el cliente deja a un servicio. Al menos uno de los dos debe
-- venir presente (chk_service_feedback_present).
CREATE TABLE service_feedback (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    request_id BIGINT UNSIGNED NOT NULL UNIQUE,

    client_id BIGINT UNSIGNED NOT NULL,

    -- Denormalizado desde service_requests(service_id): el feedback ya se enlaza por
    -- request_id, pero guardar service_id evita el JOIN/subconsulta al listar las
    -- reseñas de un servicio (detalle del servicio). Se deriva de la solicitud al crear.
    service_id BIGINT UNSIGNED NOT NULL,

    rating TINYINT UNSIGNED NULL,

    comment TEXT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_service_feedback_service (service_id),

    CONSTRAINT chk_service_feedback_rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),

    CONSTRAINT chk_service_feedback_present
        CHECK (rating IS NOT NULL OR comment IS NOT NULL),

    CONSTRAINT fk_service_feedback_request
        FOREIGN KEY (request_id)
        REFERENCES service_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_feedback_client
        FOREIGN KEY (client_id)
        REFERENCES users(id),

    CONSTRAINT fk_service_feedback_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE
);

CREATE TABLE service_feedback_tags_catalog (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    tag_name VARCHAR(150) NOT NULL UNIQUE,

    sentiment ENUM('P', 'N') NOT NULL
);

CREATE TABLE service_feedback_tags (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    feedback_id BIGINT UNSIGNED NOT NULL,

    tag_id BIGINT UNSIGNED NOT NULL,

    CONSTRAINT uq_service_feedback_tag UNIQUE (feedback_id, tag_id),

    CONSTRAINT fk_service_feedback_tags_feedback
        FOREIGN KEY (feedback_id)
        REFERENCES service_feedback(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_feedback_tags_tag
        FOREIGN KEY (tag_id)
        REFERENCES service_feedback_tags_catalog(id)
        ON DELETE CASCADE
);

-- =========================================================
-- CLIENT FEEDBACK (rating + reseña unificados)
-- =========================================================

-- Un feedback por solicitud: agrupa la calificación (opcional) y la reseña
-- (opcional) que el oferente deja al cliente. offerer_id = autor, client_id =
-- calificado. Al menos uno de los dos debe venir presente.
CREATE TABLE client_feedback (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    request_id BIGINT UNSIGNED NOT NULL UNIQUE,

    offerer_id BIGINT UNSIGNED NOT NULL,

    client_id BIGINT UNSIGNED NOT NULL,

    rating TINYINT UNSIGNED NULL,

    comment TEXT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_client_feedback_rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),

    CONSTRAINT chk_client_feedback_present
        CHECK (rating IS NOT NULL OR comment IS NOT NULL),

    CONSTRAINT fk_client_feedback_request
        FOREIGN KEY (request_id)
        REFERENCES service_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_client_feedback_offerer
        FOREIGN KEY (offerer_id)
        REFERENCES users(id),

    CONSTRAINT fk_client_feedback_client
        FOREIGN KEY (client_id)
        REFERENCES users(id)
);

CREATE TABLE client_feedback_tags_catalog (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    tag_name VARCHAR(150) NOT NULL UNIQUE,

    sentiment ENUM('P', 'N') NOT NULL
);

CREATE TABLE client_feedback_tags (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    feedback_id BIGINT UNSIGNED NOT NULL,

    tag_id BIGINT UNSIGNED NOT NULL,

    CONSTRAINT uq_client_feedback_tag UNIQUE (feedback_id, tag_id),

    CONSTRAINT fk_client_feedback_tags_feedback
        FOREIGN KEY (feedback_id)
        REFERENCES client_feedback(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_client_feedback_tags_tag
        FOREIGN KEY (tag_id)
        REFERENCES client_feedback_tags_catalog(id)
        ON DELETE CASCADE
);

-- =========================================================
-- METRICS
-- =========================================================

CREATE TABLE service_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    service_id BIGINT UNSIGNED NOT NULL UNIQUE,

    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0,

    total_ratings INT UNSIGNED NOT NULL DEFAULT 0,

    total_comments INT UNSIGNED NOT NULL DEFAULT 0,

    -- Solicitudes lógicas dirigidas al servicio. Alimentada por RequestCreatedEvent.
    total_requests_received INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_service_metrics_avg_rating (average_rating),

    CONSTRAINT fk_service_metrics_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE
);

CREATE TABLE service_tag_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    tag_id BIGINT UNSIGNED NOT NULL,

    service_id BIGINT UNSIGNED NOT NULL,

    tag_count INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_service_tag_metric UNIQUE (tag_id, service_id),

    INDEX idx_service_tag_metrics_service_count (service_id, tag_count),

    CONSTRAINT fk_service_tag_metrics_tag
        FOREIGN KEY (tag_id)
        REFERENCES service_feedback_tags_catalog(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_tag_metrics_service
        FOREIGN KEY (service_id)
        REFERENCES services(id)
        ON DELETE CASCADE
);

CREATE TABLE offerer_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    offerer_id BIGINT UNSIGNED NOT NULL UNIQUE,

    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0,

    total_ratings INT UNSIGNED NOT NULL DEFAULT 0,

    total_comments INT UNSIGNED NOT NULL DEFAULT 0,

    total_positive_tags INT UNSIGNED NOT NULL DEFAULT 0,

    total_negative_tags INT UNSIGNED NOT NULL DEFAULT 0,

    -- Solicitudes que el oferente ha recibido (creación original de solicitud, no reprogramaciones).
    total_requests_received INT UNSIGNED NOT NULL DEFAULT 0,

    total_accepted_requests INT UNSIGNED NOT NULL DEFAULT 0,

    total_completed_services INT UNSIGNED NOT NULL DEFAULT 0,

    total_cancelled_services INT UNSIGNED NOT NULL DEFAULT 0,

    -- ENVIAR propuestas de reprogramación. Se cuenta una por cada propuesta creada.
    total_reschedule_proposals_sent INT UNSIGNED NOT NULL DEFAULT 0,

    total_not_provided_services INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_offerer_metrics_user
        FOREIGN KEY (offerer_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE client_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    client_id BIGINT UNSIGNED NOT NULL UNIQUE,

    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0,

    total_ratings INT UNSIGNED NOT NULL DEFAULT 0,

    total_comments INT UNSIGNED NOT NULL DEFAULT 0,

    total_positive_tags INT UNSIGNED NOT NULL DEFAULT 0,

    total_negative_tags INT UNSIGNED NOT NULL DEFAULT 0,

    -- Solicitudes que el cliente ha enviado (creación original de solicitud, no reprogramaciones).
    total_requests_sent INT UNSIGNED NOT NULL DEFAULT 0,

    total_accepted_requests INT UNSIGNED NOT NULL DEFAULT 0,

    total_completed_requests INT UNSIGNED NOT NULL DEFAULT 0,

    total_cancelled_requests INT UNSIGNED NOT NULL DEFAULT 0,

    -- El cliente es quien reprograma (flujo libre o al aceptar una propuesta): cuenta reprogramadas.
    total_rescheduled_requests INT UNSIGNED NOT NULL DEFAULT 0,

    total_not_provided_requests INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_client_metrics_user
        FOREIGN KEY (client_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE offerer_tag_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    offerer_id BIGINT UNSIGNED NOT NULL,

    tag_id BIGINT UNSIGNED NOT NULL,

    tag_count INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_offerer_tag_metric UNIQUE (offerer_id, tag_id),

    INDEX idx_offerer_tag_metrics_offerer_count (offerer_id, tag_count),

    CONSTRAINT fk_offerer_tag_metrics_user
        FOREIGN KEY (offerer_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_offerer_tag_metrics_tag
        FOREIGN KEY (tag_id)
        REFERENCES service_feedback_tags_catalog(id)
        ON DELETE CASCADE
);

CREATE TABLE client_tag_metrics (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    client_id BIGINT UNSIGNED NOT NULL,

    tag_id BIGINT UNSIGNED NOT NULL,

    tag_count INT UNSIGNED NOT NULL DEFAULT 0,

    version BIGINT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT uq_client_tag_metric UNIQUE (client_id, tag_id),

    INDEX idx_client_tag_metrics_client_count (client_id, tag_count),

    CONSTRAINT fk_client_tag_metrics_user
        FOREIGN KEY (client_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_client_tag_metrics_tag
        FOREIGN KEY (tag_id)
        REFERENCES client_feedback_tags_catalog(id)
        ON DELETE CASCADE
);

-- =========================================================
-- REPORTS
-- =========================================================

CREATE TABLE reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    reporter_id BIGINT UNSIGNED NOT NULL,

    reported_user_id BIGINT UNSIGNED NOT NULL,

    report_type ENUM(
        'REQUEST',
        'SERVICE_FEEDBACK',
        'CLIENT_FEEDBACK'
    ) NOT NULL,

    category VARCHAR(150) NOT NULL,

    reason TEXT NOT NULL,

    status ENUM(
        'PENDING',
        'RESOLVED',
        'CLOSED'
    ) NOT NULL DEFAULT 'PENDING',

    priority ENUM(
        'LOW',
        'MEDIUM',
        'HIGH',
        'CRITICAL'
    ) NOT NULL DEFAULT 'MEDIUM',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_reports_reporter
        FOREIGN KEY (reporter_id)
        REFERENCES users(id),

    CONSTRAINT fk_reports_reported_user
        FOREIGN KEY (reported_user_id)
        REFERENCES users(id)
);

CREATE TABLE request_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    report_id BIGINT UNSIGNED NOT NULL UNIQUE,

    request_id BIGINT UNSIGNED NOT NULL,

    CONSTRAINT fk_request_reports_report
        FOREIGN KEY (report_id)
        REFERENCES reports(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_request_reports_request
        FOREIGN KEY (request_id)
        REFERENCES service_requests(id)
);

CREATE TABLE service_feedback_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    report_id BIGINT UNSIGNED NOT NULL UNIQUE,

    -- Nullable + ON DELETE SET NULL: al revertir (borrar) el feedback, el reporte sobrevive como
    -- auditoria con feedback_id = NULL (feedback eliminado). Ver NOTAS.txt (moderacion).
    feedback_id BIGINT UNSIGNED NULL,

    CONSTRAINT fk_service_feedback_reports_report
        FOREIGN KEY (report_id)
        REFERENCES reports(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_service_feedback_reports_feedback
        FOREIGN KEY (feedback_id)
        REFERENCES service_feedback(id)
        ON DELETE SET NULL
);

CREATE TABLE client_feedback_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    report_id BIGINT UNSIGNED NOT NULL UNIQUE,

    -- Nullable + ON DELETE SET NULL: al revertir (borrar) el feedback, el reporte sobrevive como
    -- auditoria con feedback_id = NULL (feedback eliminado). Ver NOTAS.txt (moderacion).
    feedback_id BIGINT UNSIGNED NULL,

    CONSTRAINT fk_client_feedback_reports_report
        FOREIGN KEY (report_id)
        REFERENCES reports(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_client_feedback_reports_feedback
        FOREIGN KEY (feedback_id)
        REFERENCES client_feedback(id)
        ON DELETE SET NULL
);

CREATE TABLE report_actions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    report_id BIGINT UNSIGNED NOT NULL,

    admin_id BIGINT UNSIGNED NOT NULL,

    action_type ENUM(
        'WARN',
        'BAN',
        'REVERT_FEEDBACK',
        'MARK_REQUEST_NOT_PROVIDED',
        'CLOSE'
    ) NOT NULL,

    action_description TEXT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_report_actions_report (report_id),

    CONSTRAINT fk_report_actions_report
        FOREIGN KEY (report_id)
        REFERENCES reports(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_report_actions_admin
        FOREIGN KEY (admin_id)
        REFERENCES users(id)
);

-- =========================================================
-- NOTIFICATION OUTBOX
-- Las tablas notifications / notification_channels / notification_deliveries se movieron a la BD propia
-- de notifications-service (backend/notifications-service, migraciones Flyway). El monolito solo guarda
-- aqui los comandos notification.requested pendientes de publicar en RabbitMQ (Transactional Outbox, M3).
-- =========================================================

CREATE TABLE notification_outbox (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

    -- Id del mensaje: clave de idempotencia con la que el consumidor descarta duplicados.
    event_id VARCHAR(36) NOT NULL UNIQUE,

    -- JSON del contrato notification.requested.v1 (correo y protectedData van sellados con AES-GCM).
    payload MEDIUMTEXT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Intentos de publicacion fallidos y ultimo error (diagnostico; el relay reintenta sin limite).
    attempts INT UNSIGNED NOT NULL DEFAULT 0,

    last_error VARCHAR(500) NULL
);

-- =========================================================
-- INITIAL DATA
-- =========================================================

INSERT INTO roles(name)
VALUES
('CLIENT'),
('OFFERER'),
('ADMIN');