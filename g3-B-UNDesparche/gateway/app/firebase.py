import firebase_admin
from firebase_admin import auth as firebase_auth, credentials

from .config import FIREBASE_CREDENTIALS_PATH


class InvalidTokenError(Exception):
    """El token de Firebase es inválido, está mal formado o expiró."""


class AuthProviderUnavailableError(Exception):
    """No se pudo contactar a Google/Firebase para validar el token."""


def init_firebase():
    """Inicializa el SDK de Firebase una sola vez."""
    try:
        firebase_admin.get_app()
    except ValueError:
        cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
        firebase_admin.initialize_app(cred)


def verify_firebase_id_token(id_token: str) -> dict:
    """
    Verifica un ID token de Firebase y devuelve el decoded token.
    Lanza InvalidTokenError si el token es inválido o expiró (-> 401) y
    AuthProviderUnavailableError si falla la red hacia Google (-> 503).
    Es una función síncrona: desde código async debe llamarse con run_in_threadpool.
    """
    init_firebase()
    try:
        return firebase_auth.verify_id_token(id_token)
    except (firebase_auth.InvalidIdTokenError, ValueError) as exc:
        raise InvalidTokenError("Token de Firebase inválido o expirado.") from exc
    except firebase_auth.CertificateFetchError as exc:
        raise AuthProviderUnavailableError("No se pudieron obtener los certificados de Firebase.") from exc
