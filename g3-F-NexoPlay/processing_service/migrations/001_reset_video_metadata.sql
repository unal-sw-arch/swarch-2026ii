-- Destructive reset for disposable test video data. Stop API, dispatcher and workers first.
-- The four application tables are replaced; other tables and MinIO objects are preserved.
BEGIN;
SELECT pg_advisory_xact_lock(80865672);
DROP TABLE IF EXISTS public.video_hls_packages;
DROP TABLE IF EXISTS public.video_variants;
DROP TABLE IF EXISTS public.video_processing_jobs;
DROP TABLE IF EXISTS public.video_metadata;

CREATE TABLE public.video_metadata (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    original_object_key TEXT,
    manifest_object_key TEXT,
    thumbnail_object_key TEXT,
    duration_seconds NUMERIC(12,3),
    size_bytes BIGINT,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT video_metadata_status_check CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);
COMMIT;
-- initialize_schema creates the job/HLS/variant tables when services start.
