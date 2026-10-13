import uuid
from firebase_admin import storage
from .firebase import init_firebase
from django.conf import settings
from django.core.exceptions import ImproperlyConfigured


def _get_bucket():
    bucket_name = settings.FIREBASE_STORAGE_BUCKET
    if not bucket_name:
        # Falla con un mensaje claro en lugar de un error opaco del SDK
        raise ImproperlyConfigured(
            "FIREBASE_STORAGE_BUCKET no está configurado; no se pueden gestionar imágenes."
        )
    return storage.bucket(bucket_name)


def upload_image(file, folder):
    """
    Sube un archivo de imágen a Firebase Storage y devuelve su URL pública.

    Args:
        file: objeto de archivo recibido en el request (request.FILES["image"])
        folder: carpeta destino dentro del bucket ("events" o "inventory")

    Returns:
        URL pública del archivo subido.
    """
    init_firebase()

    bucket = _get_bucket()

    extension = file.name.split(".")[-1]
    filename = f"{folder}/{uuid.uuid4()}.{extension}"

    blob = bucket.blob(filename)
    blob.upload_from_file(file, content_type=file.content_type)
    blob.make_public()

    return blob.public_url


def delete_image(image_url):
    """
    Elimina una imagen de Firebase Storage dada su URL pública.
    """
    init_firebase()

    bucket = _get_bucket()

    # Extrae el path relativo de la URL pública
    # URL tiene forma: https://storage.googleapis.com/bucket-name/folder/uuid.ext
    path = image_url.split(f"{settings.FIREBASE_STORAGE_BUCKET}/")[-1]
    blob = bucket.blob(path)

    if blob.exists():
        blob.delete()
