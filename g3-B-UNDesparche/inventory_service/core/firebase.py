# core/firebase.py
import firebase_admin
from django.conf import settings
from firebase_admin import credentials


def init_firebase():
    try:
        return firebase_admin.get_app()
    except ValueError:
        cred = credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
        # El bucket es opcional: la verificación de tokens no lo necesita, solo Storage
        bucket = getattr(settings, "FIREBASE_STORAGE_BUCKET", None)
        options = {"storageBucket": bucket} if bucket else None
        return firebase_admin.initialize_app(cred, options)