"""Comprobantes en RustFS (compatible con S3), bucket `receipts`. Se usa el SDK `minio`, que habla S3."""
import io

from minio import Minio
from minio.error import S3Error

import config


class ReceiptStorage:
    def __init__(self, client: Minio | None = None, bucket: str | None = None):
        self._client = client or Minio(
            config.S3_ENDPOINT_HOST,
            access_key=config.S3_ACCESS_KEY,
            secret_key=config.S3_SECRET_KEY,
            secure=config.S3_SECURE,
            region=config.S3_REGION,  # evita consultar la región al servidor
        )
        self._bucket = bucket or config.RECEIPTS_BUCKET
        self._bucket_ready = False

    def _ensure_bucket(self) -> None:
        if not self._bucket_ready:
            if not self._client.bucket_exists(self._bucket):
                self._client.make_bucket(self._bucket)  # normalmente lo crea el job rustfs-init
            self._bucket_ready = True

    def exists(self, object_name: str) -> bool:
        self._ensure_bucket()
        try:
            self._client.stat_object(self._bucket, object_name)
            return True
        except S3Error as exc:
            if exc.code in ("NoSuchKey", "NoSuchObject", "NotFound"):
                return False
            raise

    def put(self, object_name: str, data: bytes, content_type: str = "application/pdf") -> None:
        self._ensure_bucket()
        self._client.put_object(
            self._bucket, object_name, io.BytesIO(data), length=len(data), content_type=content_type
        )
