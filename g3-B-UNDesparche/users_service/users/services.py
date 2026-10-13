from firebase_admin import auth as firebase_auth
from .models import User


def build_claims(user):
    return {"role": user.role}


def sync_claims(user, current_claims=None):
    """
    Publica los claims del usuario en Firebase.
    Si current_claims (los del token actual) ya coinciden, no hace nada.
    Devuelve True si hubo cambios: el cliente debe renovar su token.
    """
    if not user.firebase_uid:
        return False

    claims = build_claims(user)
    if current_claims is not None and all(
        current_claims.get(key) == value for key, value in claims.items()
    ):
        return False

    try:
        firebase_auth.set_custom_user_claims(user.firebase_uid, claims)
        return True
    except Exception:
        return False


def change_role(user, new_role):
    if new_role not in User.Role.values:
        raise ValueError(f"Invalid role: {new_role}")

    user.role = new_role
    user.save(update_fields=["role"])

    sync_claims(user)
