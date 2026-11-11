#!/bin/sh
# Aplica migraciones (con reintentos mientras PostgreSQL arranca) y levanta la API.
set -e
tries=0
until alembic upgrade head; do
  tries=$((tries + 1))
  if [ "$tries" -ge 30 ]; then
    echo "No se pudo migrar la base de datos tras $tries intentos" >&2
    exit 1
  fi
  echo "Migración fallida; reintentando en 2 s ($tries/30)..." >&2
  sleep 2
done
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
