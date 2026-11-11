-- =============================================================================
-- Panadería Express — Prototipo 2
-- Inicialización de PostgreSQL.
--
-- Este script solo crea las bases, los esquemas y las extensiones.
-- Las tablas las crean las migraciones de cada servicio:
--   identity-service  -> Flyway
--   order-service     -> Alembic
--   kitchen-service   -> SQL propio
--   reporting-service -> solo lectura
-- =============================================================================

-- Extensiones necesarias para UUID y búsquedas sin distinguir mayúsculas.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- Esquemas por contexto delimitado.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS kitchen;
CREATE SCHEMA IF NOT EXISTS reporting;

COMMENT ON SCHEMA auth IS 'Identity Service: usuarios, roles, permisos y auditoría';
COMMENT ON SCHEMA orders IS 'Order Service: pedidos, pagos, reservas y outbox';
COMMENT ON SCHEMA kitchen IS 'Kitchen Service: tiempos base, capacidad e historial de estados';
COMMENT ON SCHEMA reporting IS 'Reporting Service: vistas de solo lectura y agregados';

-- El usuario de la aplicación es el dueño de los esquemas.
-- POSTGRES_USER crea la base y ya existe cuando corre este script.
DO $$
DECLARE
    app_user text := current_user;
BEGIN
    EXECUTE format('ALTER SCHEMA auth OWNER TO %I', app_user);
    EXECUTE format('ALTER SCHEMA orders OWNER TO %I', app_user);
    EXECUTE format('ALTER SCHEMA kitchen OWNER TO %I', app_user);
    EXECUTE format('ALTER SCHEMA reporting OWNER TO %I', app_user);
END
$$;

-- Zona horaria de referencia para los eventos y el historial.
DO $$
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET timezone TO %L',
        current_database(),
        'America/Bogota'
    );
END
$$;
