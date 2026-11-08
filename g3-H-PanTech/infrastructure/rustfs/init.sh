#!/bin/sh
# =============================================================================
# Panadería Express — Prototipo 2
# Inicialización de buckets de RustFS (almacenamiento compatible con S3).
#
# Se ejecuta con el cliente AWS CLI en un contenedor de un solo uso:
#   docker compose -f docker-compose.p2.yml --env-file .env.p2 \
#     --profile init run --rm rustfs-init
#
# No forma parte de los contenedores permanentes del sistema.
# =============================================================================

set -eu

: "${S3_ENDPOINT:=http://rustfs:9000}"
: "${S3_ACCESS_KEY:=panaderia}"
: "${S3_SECRET_KEY:=panaderia123}"
: "${S3_REGION:=us-east-1}"
: "${S3_BUCKET_RECEIPTS:=receipts}"
: "${S3_BUCKET_PRODUCTS:=products}"
: "${S3_BUCKET_REPORTS:=reports}"

export AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY}"
export AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY}"
export AWS_DEFAULT_REGION="${S3_REGION}"

s3() {
  aws --endpoint-url "${S3_ENDPOINT}" "$@"
}

echo "[rustfs-init] Esperando a RustFS en ${S3_ENDPOINT}..."
until s3 s3api list-buckets >/dev/null 2>&1; do
  sleep 2
done

for bucket in "${S3_BUCKET_RECEIPTS}" "${S3_BUCKET_PRODUCTS}" "${S3_BUCKET_REPORTS}"; do
  if s3 s3api head-bucket --bucket "${bucket}" >/dev/null 2>&1; then
    echo "[rustfs-init] El bucket ${bucket} ya existe."
  else
    echo "[rustfs-init] Creando bucket ${bucket}..."
    s3 s3api create-bucket --bucket "${bucket}" >/dev/null
  fi
done

# Las imágenes de productos son públicas para el storefront.
# Comprobantes y reportes quedan privados; se acceden con URL firmada.
echo "[rustfs-init] Aplicando lectura pública al catálogo..."
s3 s3api put-bucket-policy \
  --bucket "${S3_BUCKET_PRODUCTS}" \
  --policy "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::${S3_BUCKET_PRODUCTS}/*\"]}]}"

echo "[rustfs-init] Listo."
s3 s3api list-buckets --query "Buckets[].Name" --output text
