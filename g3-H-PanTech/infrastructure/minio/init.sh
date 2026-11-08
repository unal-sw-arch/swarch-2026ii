#!/bin/sh
# =============================================================================
# Panadería Express — Prototipo 2
# Inicialización de buckets de MinIO.
#
# Se ejecuta con el cliente `mc` en un contenedor de un solo uso:
#   docker compose -f docker-compose.p2.yml --env-file .env.p2 \
#     --profile init run --rm minio-init
#
# No forma parte de los 12 contenedores del sistema en ejecución.
# =============================================================================

set -eu

: "${MINIO_ENDPOINT:=http://minio:9000}"
: "${MINIO_ROOT_USER:=panaderia}"
: "${MINIO_ROOT_PASSWORD:=panaderia123}"
: "${MINIO_BUCKET_RECEIPTS:=receipts}"
: "${MINIO_BUCKET_PRODUCTS:=products}"
: "${MINIO_BUCKET_REPORTS:=reports}"

echo "[minio-init] Esperando a MinIO en ${MINIO_ENDPOINT}..."
until mc alias set local "${MINIO_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" >/dev/null 2>&1; do
  sleep 2
done

for bucket in "${MINIO_BUCKET_RECEIPTS}" "${MINIO_BUCKET_PRODUCTS}" "${MINIO_BUCKET_REPORTS}"; do
  echo "[minio-init] Creando bucket ${bucket}..."
  mc mb --ignore-existing "local/${bucket}"
done

# Las imágenes de productos son públicas para el storefront.
mc anonymous set download local/"${MINIO_BUCKET_PRODUCTS}"

# Comprobantes y reportes quedan privados; se acceden con URL firmada.
mc anonymous set none local/"${MINIO_BUCKET_RECEIPTS}" || true
mc anonymous set none local/"${MINIO_BUCKET_REPORTS}" || true

echo "[minio-init] Listo."
mc ls local
