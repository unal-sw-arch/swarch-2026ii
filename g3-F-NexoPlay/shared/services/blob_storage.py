import os
from typing import BinaryIO

import boto3
from boto3.s3.transfer import TransferConfig
from botocore.config import Config
from botocore.exceptions import ClientError


class BlobStorageService:
    """Stores video streams in the configured MinIO S3 bucket."""

    def __init__(self) -> None:
        self.bucket_name = os.getenv("MINIO_BUCKET_NAME", "streaming-videos")
        self._client = boto3.client(
            "s3",
            endpoint_url=os.getenv("MINIO_ENDPOINT_URL", "http://localhost:9000"),
            aws_access_key_id=os.getenv("MINIO_ROOT_USER"),
            aws_secret_access_key=os.getenv("MINIO_ROOT_PASSWORD"),
            region_name="us-east-1",
            config=Config(
                signature_version="s3v4",
                s3={"addressing_style": "path"},
                connect_timeout=5,
                read_timeout=60,
                retries={"mode": "standard", "total_max_attempts": 3},
                request_checksum_calculation="when_required",
                response_checksum_validation="when_required",
            ),
        )
        self._transfer_config = TransferConfig(preferred_transfer_client="classic")

    def ensure_bucket_exists(self) -> None:
        try:
            self.check_connection()
        except ClientError as error:
            code = error.response.get("Error", {}).get("Code", "")
            if str(code) not in {"404", "NoSuchBucket", "NotFound"}:
                raise
            try:
                self._client.create_bucket(Bucket=self.bucket_name)
            except ClientError as create_error:
                # Another upload may have created the same bucket concurrently.
                if create_error.response.get("Error", {}).get("Code") != "BucketAlreadyOwnedByYou":
                    raise

    def check_connection(self) -> None:
        self._client.head_bucket(Bucket=self.bucket_name)

    def upload(self, stream: BinaryIO, object_key: str, content_type: str) -> None:
        self.ensure_bucket_exists()
        stream.seek(0)
        self._client.upload_fileobj(
            stream,
            self.bucket_name,
            object_key,
            ExtraArgs={"ContentType": content_type},
            Config=self._transfer_config,
        )

    def delete(self, object_key: str) -> None:
        self._client.delete_object(Bucket=self.bucket_name, Key=object_key)

    def delete_prefix(self, bucket: str, prefix: str) -> None:
        pages = self._client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=prefix)
        for page in pages:
            objects = [{"Key": item["Key"]} for item in page.get("Contents", [])]
            if not objects:
                continue
            result = self._client.delete_objects(
                Bucket=bucket, Delete={"Objects": objects, "Quiet": True},
            )
            if result.get("Errors"):
                error = result["Errors"][0]
                raise ClientError({"Error": error}, "DeleteObjects")

    def download(self, bucket: str, object_key: str, destination: str) -> None:
        self._client.download_file(
            bucket, object_key, destination, Config=self._transfer_config
        )

    def open_object(self, bucket: str, object_key: str):
        return self._client.get_object(Bucket=bucket, Key=object_key)

    def close(self) -> None:
        self._client.close()
