-- =========================================================
-- notifications-service — esquema inicial (V1)
-- Base de datos PROPIA del servicio (serviya_notifications). Derivado de las tablas que tenía el monolito
-- en mysql/01_create_database.sql, con estos cambios:
--   * notifications.user_id ya NO es FK a users (users vive en la BD de identity/monolito).
--   * notifications.source_event_id: id del mensaje del bus; UNIQUE = idempotencia del consumidor.
--   * notifications.recipient_email: correo del destinatario (cifrado AES-GCM, VARBINARY) para reintentos.
--   * columnas version (@Version, bloqueo optimista B10) en las tres tablas.
--   * índices para la bandeja por usuario y para el reintento (estado + intentos).
-- =========================================================

CREATE TABLE notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version INT NULL,
    source_event_id VARCHAR(36) NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    entity_type VARCHAR(100) NULL,
    entity_id BIGINT UNSIGNED NULL,
    recipient_email VARBINARY(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notifications_source_event UNIQUE (source_event_id),
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_created_at (created_at)
);

CREATE TABLE notification_channels (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version INT NULL,
    name VARCHAR(50) NOT NULL,

    CONSTRAINT uk_notification_channels_name UNIQUE (name)
);

CREATE TABLE notification_deliveries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    version INT NULL,
    notification_id BIGINT UNSIGNED NOT NULL,
    channel_id INT UNSIGNED NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    read_at DATETIME NULL,
    sent_at DATETIME NULL,
    -- Intentos de envío realizados (el primero cuenta como 1). Lo usa el reintento programado.
    attempts INT UNSIGNED NOT NULL DEFAULT 0,

    CONSTRAINT chk_notification_deliveries_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'READ')),
    INDEX idx_notification_deliveries_notification (notification_id),
    INDEX idx_notification_deliveries_retry (delivery_status, attempts),

    CONSTRAINT fk_notification_deliveries_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_deliveries_channel
        FOREIGN KEY (channel_id) REFERENCES notification_channels(id)
);

-- Los ids 1 y 2 coinciden con los del monolito: los datos migrados conservan su channel_id.
INSERT INTO notification_channels (id, name) VALUES (1, 'INTERNAL'), (2, 'EMAIL');
