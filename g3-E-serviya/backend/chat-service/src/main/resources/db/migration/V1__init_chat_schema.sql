-- chat-service: esquema inicial (guía del chat §7.2). MySQL 8.4, utf8mb4, fechas DATETIME(6) en UTC.
-- Todos los ids de ServiYa (usuarios, solicitudes) son BIGINT. client_message_id es un token opaco del cliente.

CREATE TABLE conversations (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    type              VARCHAR(20)  NOT NULL,                 -- REQUEST | DIRECT | SUPPORT
    context_id        BIGINT       NULL,                     -- request_id del monolito cuando type = REQUEST
    status            VARCHAR(20)  NOT NULL,                 -- ACTIVE | READ_ONLY | RESOLVED
    context_status    VARCHAR(40)  NULL,                     -- estado de la solicitud, copiado tal cual (filtros, ADR-012)
    context_status_at DATETIME(6)  NULL,                     -- occurred_at del evento que lo fijó (descarta eventos viejos)
    last_seq          BIGINT       NOT NULL DEFAULT 0,
    last_message_at   DATETIME(6)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    -- NULL salvo para REQUEST: un UNIQUE normal admite varios NULL, así la unicidad solo aplica a REQUEST.
    request_ctx       BIGINT GENERATED ALWAYS AS (IF(type = 'REQUEST', context_id, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT ck_conv_type   CHECK (type IN ('REQUEST', 'DIRECT', 'SUPPORT')),
    CONSTRAINT ck_conv_status CHECK (status IN ('ACTIVE', 'READ_ONLY', 'RESOLVED')),
    UNIQUE KEY ux_conv_request (request_ctx),
    KEY ix_conv_last_message (last_message_at DESC, id DESC)
) ENGINE = InnoDB;

CREATE TABLE conversation_participants (
    conversation_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    role            VARCHAR(20) NOT NULL,                    -- CLIENT | OFFERER | ADMIN
    last_read_seq   BIGINT      NOT NULL DEFAULT 0,
    archived_at     DATETIME(6) NULL,                        -- fase 2
    joined_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_part_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT ck_part_role CHECK (role IN ('CLIENT', 'OFFERER', 'ADMIN')),
    KEY ix_participant_user (user_id, conversation_id)
) ENGINE = InnoDB;

CREATE TABLE messages (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id   BIGINT      NOT NULL,
    seq               BIGINT      NOT NULL,
    sender_id         BIGINT      NULL,                      -- NULL en mensajes SYSTEM
    client_message_id CHAR(36)    NULL,                      -- UUID del cliente; NULL en mensajes SYSTEM
    kind              VARCHAR(20) NOT NULL,                  -- TEXT | SYSTEM | ATTACHMENT
    flags             JSON        NULL,                      -- p. ej. ["CONTACT_INFO"]
    body              TEXT        NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,                      -- fase 2
    PRIMARY KEY (id),
    CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT ck_msg_kind CHECK (kind IN ('TEXT', 'SYSTEM', 'ATTACHMENT')),
    -- Orden total sin huecos dentro de la conversación (RNF-CHAT-07).
    UNIQUE KEY ux_msg_conv_seq (conversation_id, seq),
    -- Idempotencia de envío (RF-CHAT-16). Los NULL (mensajes SYSTEM) no chocan entre sí.
    UNIQUE KEY ux_msg_client_id (conversation_id, sender_id, client_message_id)
) ENGINE = InnoDB;

-- Outbox transaccional (ADR-009): los eventos salen a RabbitMQ desde aquí.
CREATE TABLE outbox_events (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_id      CHAR(36)     NOT NULL,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key   VARCHAR(150) NOT NULL,
    payload       JSON         NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    published_at  DATETIME(6)  NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY ux_outbox_event_id (event_id),
    KEY ix_outbox_pending (published_at, id)
) ENGINE = InnoDB;

-- Consumo idempotente (RNF-CHAT-09): un event_id procesado no se vuelve a aplicar.
CREATE TABLE processed_events (
    event_id     CHAR(36)    NOT NULL,
    event_type   VARCHAR(150) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB;

-- Proyección local de usuarios (ADR-006): solo lo mínimo para autorizar y mostrar; nada de email ni teléfono.
-- No hay proyección de solicitudes: la conversación ya guarda sus participantes y el estado copiado (ADR-012).
CREATE TABLE user_directory (
    user_id      BIGINT       NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    avatar_url   VARCHAR(500) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE | SUSPENDED | DELETED
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
) ENGINE = InnoDB;
