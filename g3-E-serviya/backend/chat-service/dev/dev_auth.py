#!/usr/bin/env python3
"""Emisor de tokens de DESARROLLO para el chat-service (ES256 + JWKS), sin dependencias: stdlib + `openssl`.

Mientras Auth no publique su JWKS real (documents/microservicios/guia-implementacion-auth.md), esto permite
levantar y probar el chat a mano con el mismo esquema de verificación que usará en producción.

    python3 dev_auth.py init                       # genera la clave EC P-256 (una vez)
    python3 dev_auth.py serve --port 9400          # sirve /.well-known/jwks.json
    python3 dev_auth.py token --user 1001 --roles CLIENT

El chat debe arrancar con JWT_JWKS_URL apuntando a este servidor y JWT_ISSUER igual a --issuer.
NO usar fuera de desarrollo: la clave privada vive en texto plano en dev/.dev-auth/.
"""
import argparse
import base64
import http.server
import json
import pathlib
import subprocess
import time

HERE = pathlib.Path(__file__).resolve().parent
KEY = HERE / ".dev-auth" / "es256-key.pem"
KID = "dev-1"
DEFAULT_ISSUER = "https://auth.serviya.local"  # el acordado (decisiones-infraestructura-implementacion.md §5)
DEFAULT_AUDIENCE = "marketplace-api"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def openssl(*args, data=None) -> bytes:
    return subprocess.run(["openssl", *args], input=data, check=True, capture_output=True).stdout


def require_key():
    if not KEY.exists():
        raise SystemExit(f"No existe {KEY}. Ejecuta primero: python3 {pathlib.Path(__file__).name} init")


def public_jwk() -> dict:
    require_key()
    der = openssl("ec", "-in", str(KEY), "-pubout", "-outform", "DER")
    point = der[-65:]  # 0x04 || X(32) || Y(32): punto sin comprimir al final del SubjectPublicKeyInfo
    if point[0] != 4:
        raise SystemExit("Clave pública inesperada")
    return {"kty": "EC", "crv": "P-256", "kid": KID, "use": "sig", "alg": "ES256",
            "x": b64url(point[1:33]), "y": b64url(point[33:65])}


def der_to_raw(sig: bytes) -> bytes:
    """ECDSA DER (SEQUENCE{INTEGER r, INTEGER s}) → r||s de 32 bytes cada uno, como exige JWS."""
    def read_int(buf, i):
        assert buf[i] == 0x02
        length = buf[i + 1]
        value = buf[i + 2:i + 2 + length]
        return value.lstrip(b"\x00").rjust(32, b"\x00"), i + 2 + length
    assert sig[0] == 0x30
    i = 3 if sig[1] & 0x80 else 2
    r, i = read_int(sig, i)
    s, _ = read_int(sig, i)
    return r + s


def make_token(user: int, roles, issuer: str, audience: str, ttl: int) -> str:
    require_key()
    now = int(time.time())
    header = {"alg": "ES256", "typ": "at+jwt", "kid": KID}
    claims = {"sub": str(user), "roles": roles, "iss": issuer, "aud": audience, "iat": now, "exp": now + ttl}
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(claims).encode())}"
    der = openssl("dgst", "-sha256", "-sign", str(KEY), data=signing_input.encode())
    return f"{signing_input}.{b64url(der_to_raw(der))}"


def cmd_init(_):
    KEY.parent.mkdir(exist_ok=True)
    if KEY.exists():
        print(f"Ya existe {KEY}")
        return
    openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(KEY))
    KEY.chmod(0o600)
    print(f"Clave generada en {KEY}")


def cmd_serve(args):
    body = json.dumps({"keys": [public_jwk()]}).encode()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/.well-known/jwks.json":
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    print(f"JWKS en http://{args.host}:{args.port}/.well-known/jwks.json (iss esperado por el chat: {args.issuer})")
    http.server.ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()


def cmd_token(args):
    print(make_token(args.user, args.roles.split(","), args.issuer, args.audience, args.ttl))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(required=True)
    sub.add_parser("init", help="genera la clave EC P-256").set_defaults(func=cmd_init)
    s = sub.add_parser("serve", help="sirve el JWKS")
    s.add_argument("--host", default="0.0.0.0")
    s.add_argument("--port", type=int, default=9400)
    s.add_argument("--issuer", default=DEFAULT_ISSUER)
    s.set_defaults(func=cmd_serve)
    t = sub.add_parser("token", help="emite un access token")
    t.add_argument("--user", type=int, required=True)
    t.add_argument("--roles", default="CLIENT", help="separados por coma: CLIENT,OFFERER,ADMIN")
    t.add_argument("--issuer", default=DEFAULT_ISSUER)
    t.add_argument("--audience", default=DEFAULT_AUDIENCE)
    t.add_argument("--ttl", type=int, default=3600, help="segundos de vida")
    t.set_defaults(func=cmd_token)
    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
