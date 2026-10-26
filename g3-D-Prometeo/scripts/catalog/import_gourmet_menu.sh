#!/usr/bin/env sh

set -eu

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIRECTORY=$(dirname -- "$(dirname -- "$SCRIPT_DIRECTORY")")
TSX="$PROJECT_DIRECTORY/node_modules/.bin/tsx"

if [ ! -x "$TSX" ]; then
  echo "No se encontró tsx. Ejecuta primero: npm ci" >&2
  exit 1
fi

cd "$PROJECT_DIRECTORY"
exec "$TSX" scripts/catalog/import_gourmet_menu.ts
