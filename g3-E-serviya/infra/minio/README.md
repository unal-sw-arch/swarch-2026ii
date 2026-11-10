# MinIO / Silo — almacenamiento de objetos para adjuntos del chat (fase 3)

Las imágenes comunitarias de MinIO desaparecieron de Docker Hub/Quay en 2026. Para no depender de
una imagen privada se usa **`pgsty/silo`**, un fork compatible con la API S3 y con las mismas
herramientas (`mc`/`mcli`). El contrato para el chat sigue siendo S3/MinIO.

Se levanta junto al compose raíz con el profile `minio`:

```bash
docker compose --profile minio up -d minio minio-init
```

- API S3: `http://localhost:9000` (solo desarrollo; el navegador usa esta URL para las URLs prefirmadas).
- Consola: `http://localhost:9001` (solo desarrollo).
- Bucket privado: `chat-attachments`.
- Red compartida: `serviya-redis-net` (el chat-service lo alcanza como `http://minio:9000`).
- El contenedor `minio-init` crea el bucket, aplica CORS (`cors.xml`) y crea el usuario `chat` con
  una política limitada al bucket.

## Variables

Añadir al `.env` de la raíz:

```env
MINIO_ROOT_USER=
MINIO_ROOT_PASSWORD=
MINIO_CHAT_ACCESS_KEY=
MINIO_CHAT_SECRET_KEY=
MINIO_BUCKET=chat-attachments
MINIO_REGION=us-east-1
S3_INTERNAL_ENDPOINT=http://minio:9000
S3_PUBLIC_ENDPOINT=http://localhost:9000
```

`S3_PUBLIC_ENDPOINT` es el host que verá el navegador en la URL prefirmada. En local es
`http://localhost:9000`; en un despliegue real debe ser el host público de MinIO.

## Seguridad

- El bucket es privado; no se permite acceso anónimo.
- El chat-service debe autorizar al participante antes de firmar la subida/descarga.
- La URL prefirmada es de vida corta.
- Validar MIME real y tamaño después de la subida (o con una política presignada).
- No exponer la consola ni la API S3 fuera de desarrollo.
