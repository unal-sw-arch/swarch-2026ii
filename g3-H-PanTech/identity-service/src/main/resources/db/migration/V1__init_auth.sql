-- =============================================================================
-- Identity Service — esquema auth
-- Usuarios, credenciales, roles, permisos, refresh tokens y auditoría.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(255) NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    CONSTRAINT users_email_not_blank CHECK (length(trim(email)) > 0)
);

CREATE TABLE auth.roles (
    id          uuid        PRIMARY KEY,
    name        varchar(50) NOT NULL UNIQUE,
    description varchar(255) NOT NULL DEFAULT '',
    level       integer     NOT NULL DEFAULT 0,
    is_staff    boolean     NOT NULL DEFAULT false,
    CONSTRAINT roles_level_positive CHECK (level >= 0)
);

CREATE TABLE auth.permissions (
    id          uuid        PRIMARY KEY,
    code        varchar(80) NOT NULL UNIQUE,
    description varchar(255) NOT NULL DEFAULT ''
);

CREATE TABLE auth.user_roles (
    user_id     uuid        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    role_id     uuid        NOT NULL REFERENCES auth.roles (id) ON DELETE CASCADE,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by uuid        REFERENCES auth.users (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE auth.role_permissions (
    role_id       uuid NOT NULL REFERENCES auth.roles (id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES auth.permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE auth.refresh_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auth.audit_log (
    id         uuid        PRIMARY KEY,
    actor_id   uuid        REFERENCES auth.users (id) ON DELETE SET NULL,
    action     varchar(80) NOT NULL,
    entity     varchar(80) NOT NULL,
    entity_id  varchar(120),
    details    text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email_lower ON auth.users (lower(email));
CREATE INDEX idx_user_roles_role ON auth.user_roles (role_id);
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON auth.refresh_tokens (expires_at);
CREATE INDEX idx_audit_log_created ON auth.audit_log (created_at DESC);
CREATE INDEX idx_audit_log_action ON auth.audit_log (action);

COMMENT ON TABLE auth.users IS 'Cuentas personales de clientes y empleados';
COMMENT ON TABLE auth.roles IS 'Roles con nivel jerárquico y marca de personal';
COMMENT ON TABLE auth.permissions IS 'Permisos atómicos recurso:acción';
COMMENT ON TABLE auth.refresh_tokens IS 'Refresh tokens opacos hasheados (7 días)';
COMMENT ON TABLE auth.audit_log IS 'Registro de acciones sensibles';
