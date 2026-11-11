"""Genera un par de claves RSA (carpeta ./keys) y firma JWT RS256 de prueba, como lo haría identity-service.

    pip install "PyJWT[crypto]"
    python gen_token.py cliente            # imprime el token
    python gen_token.py mesero --sub waiter-1
Perfiles: cliente | mesero | supervisor | admin | cocinero
La clave pública queda en keys/public.pem (úsala como JWT_PUBLIC_KEY / Jwt__PublicKeyPem).
"""
import argparse
import time
from pathlib import Path

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

PROFILES = {
    "cliente": ["orders:create_own", "orders:read_own", "reservations:manage_own", "history:read_own"],
    "mesero": ["orders:create", "orders:charge", "reservations:manage_all"],
    "supervisor": ["orders:create", "orders:charge", "reservations:manage_all", "orders:read_all", "history:read_all", "reports:read"],
    "admin": ["orders:create", "orders:charge", "reservations:manage_all", "orders:read_all", "history:read_all",
              "reports:read", "products:manage"],
    "cocinero": ["kitchen:read", "kitchen:update"],
}

keys = Path(__file__).parent / "keys"


def load_or_create_keys():
    private, public = keys / "private.pem", keys / "public.pem"
    if not private.exists():
        keys.mkdir(exist_ok=True)
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        private.write_bytes(key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        public.write_bytes(key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo))
    return private.read_bytes()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=PROFILES)
    parser.add_argument("--sub", help="id de usuario (por defecto: <perfil>-1)")
    parser.add_argument("--hours", type=float, default=8)
    args = parser.parse_args()

    now = int(time.time())
    sub = args.sub or f"{args.profile}-1"
    payload = {
        "sub": sub, "email": f"{sub}@example.com", "roles": [args.profile], "permissions": PROFILES[args.profile], "aud": "panaderia-express",
        "level": 10, "iss": "identity-service", "iat": now, "exp": now + int(args.hours * 3600),
    }
    print(jwt.encode(payload, load_or_create_keys(), algorithm="RS256"))
