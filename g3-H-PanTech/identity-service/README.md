# Identity Service

Servicio de identidad de **Panadería Express** (Prototipo 2). Administra cuentas,
credenciales, roles, permisos, jerarquía, refresh tokens y auditoría, y emite los
JWT que consume el resto del sistema a través del API Gateway.

| Campo | Valor |
|---|---|
| Lenguaje | Kotlin 1.9 · Spring Boot 3.3 |
| Puerto interno | `8002` |
| Base de datos | PostgreSQL, esquema `auth` (Flyway) |
| Caché | Redis (opcional; PostgreSQL es la fuente de verdad) |
| Firma JWT | RS256 |
| Emisor / audiencia | `identity-service` / `panaderia-express` |

---

## 1. Endpoints

### Públicos

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/auth/register` | Registro público. **Siempre** crea una cuenta `cliente`. |
| `POST` | `/auth/login` | Valida credenciales y devuelve access + refresh token. |
| `POST` | `/auth/refresh` | Rota el refresh token y emite un access token nuevo. |
| `GET` | `/.well-known/jwks.json` | Clave pública RSA en formato JWKS para validar los JWT. |
| `GET` | `/health` | Liveness: `200 {"status":"ok"}`. |
| `GET` | `/ready` | Readiness: PostgreSQL obligatorio; Redis se reporta como caché opcional. |

### Protegidos

| Método | Ruta | Permiso requerido | Descripción |
|---|---|---|---|
| `POST` | `/auth/logout` | Autenticado | Revoca el refresh token recibido. |
| `GET` | `/auth/me` | Autenticado | Usuario, roles, permisos y nivel efectivos. |
| `GET` | `/users` | `users:manage` o `users:manage_operational` | Lista cuentas visibles para el actor. |
| `GET` | `/users/{id}` | `users:manage` o `users:manage_operational` | Detalle de una cuenta. |
| `POST` | `/users` | `users:manage` o `users:manage_operational` | Crea una cuenta de personal. |
| `PATCH` | `/users/{id}` | `users:manage` o `users:manage_operational` | Actualiza datos o desactiva la cuenta. |
| `POST` | `/users/{id}/roles` | `roles:manage` | Asigna un rol. |
| `DELETE` | `/users/{id}/roles/{role}` | `roles:manage` | Retira un rol. |
| `GET` | `/roles` | `roles:manage` | Matriz de roles. |
| `GET` | `/permissions` | `roles:manage` | Catálogo de permisos. |
| `GET` | `/audit` | `users:manage` | Últimas acciones sensibles (`limit`, `action`). |

### Ejemplos

```bash
# Registro público (no acepta roles en el cuerpo)
curl -i -X POST http://localhost:8080/auth/register \
  -H 'content-type: application/json' \
  -d '{"email":"nuevo@correo.com","password":"Demo1234!","full_name":"Nuevo Cliente"}'

# Login
curl -s -X POST http://localhost:8080/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"cliente@demo.com","password":"Demo1234!"}'

# Sesión actual (a través del Gateway o directo al servicio)
curl -s http://localhost:8080/auth/me \
  -H "authorization: Bearer $ACCESS_TOKEN"

# Refresh
curl -s -X POST http://localhost:8080/auth/refresh \
  -H 'content-type: application/json' \
  -d "{\"refresh_token\":\"$REFRESH_TOKEN\"}"

# Logout
curl -i -X POST http://localhost:8080/auth/logout \
  -H "authorization: Bearer $ACCESS_TOKEN" \
  -H 'content-type: application/json' \
  -d "{\"refresh_token\":\"$REFRESH_TOKEN\"}"
```

Respuesta de login/registro:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "opaco-de-7-dias",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800,
  "user": {
    "id": "30000000-0000-0000-0000-000000000001",
    "email": "cliente@demo.com",
    "full_name": "Cliente Demo",
    "active": true,
    "roles": ["cliente"],
    "permissions": ["orders:create_own", "orders:read_own", "reservations:manage_own", "history:read_own"],
    "level": 10,
    "created_at": "2026-01-01T00:00:00Z",
    "last_login_at": "2026-01-01T00:00:00Z"
  }
}
```

---

## 2. JWT y JWKS

- Algoritmo **RS256**.
- Access token: **15 minutos** (`JWT_ACCESS_TTL_SECONDS=900`).
- Refresh token: **7 días** (`JWT_REFRESH_TTL_SECONDS=604800`), opaco, guardado
  como hash SHA-256 en `auth.refresh_tokens` y cacheado en Redis.
- Rotación obligatoria: cada refresh revoca el token usado.
- El servicio acepta dos fuentes de identidad:
  1. `Authorization: Bearer <jwt>` (llamadas directas y smoke tests).
  2. Encabezados del Gateway: `X-User-Id`, `X-User-Email`, `X-Roles`,
     `X-Permissions`, `X-Level`.

Claims emitidos:

| Claim | Tipo | Ejemplo |
|---|---|---|
| `sub` | string (UUID) | `"30000000-0000-0000-0000-000000000001"` |
| `email` | string | `"cliente@demo.com"` |
| `roles` | array | `["mesero", "despachador"]` |
| `permissions` | array | `["orders:create", "orders:charge"]` |
| `level` | integer | `50` |
| `iss` | string | `"identity-service"` |
| `aud` | string | `"panaderia-express"` |
| `iat` / `exp` | integer | epoch en segundos |

JWKS: `GET http://identity-service:8002/.well-known/jwks.json`. Expone un `kid`
(thumbprint RFC 7638 de la clave pública) que también viaja en el encabezado de
cada JWT, así los consumidores eligen la clave correcta. El `kid` es estable
entre reinicios porque se deriva de la clave persistida.

Las claves RSA se leen de `JWT_PRIVATE_KEY_PATH` y `JWT_PUBLIC_KEY_PATH`. Si los
archivos no existen (entorno local), el servicio genera un par de 2048 bits y
lo intenta persistir en el volumen `identity_keys`. En producción se montan como
secretos y no se generan en caliente.

---

## 3. Modelo de autorización

Reglas de negocio implementadas:

- El registro público **solo** crea `cliente`; cualquier rol enviado en el
  cuerpo se ignora.
- Un `admin` puede gestionar cualquier cuenta y asignar roles.
- Un `supervisor` con `users:manage_operational` solo puede gestionar cuentas
  operativas: no crea administradores ni modifica roles.
- Las cuentas **no se eliminan**: se desactivan (`active=false`). Un usuario
  desactivado no puede iniciar sesión ni refrescar.
- Los permisos efectivos son la **unión** de los permisos de todos los roles.
- El nivel efectivo es el **máximo** de los niveles de los roles.
- `products:manage` e `history:*` son permisos, no roles.

### Roles y niveles

| Nivel | Rol | Personal | Permisos principales |
|---:|---|---|---|
| 100 | `admin` | sí | todos |
| 80 | `supervisor` | sí | operación, historial, reportes, cuentas operativas |
| 50 | `mesero` | sí | `orders:create`, `orders:charge`, `reservations:manage_all` |
| 50 | `cocinero` | sí | `kitchen:read`, `kitchen:update` |
| 50 | `despachador` | sí | `kitchen:read`, `kitchen:deliver` |
| 10 | `cliente` | no | `orders:create_own`, `orders:read_own`, `reservations:manage_own`, `history:read_own` |

La matriz completa vive en `V2__seed_auth.sql`.

### Usuarios demo

Todos usan la contraseña **`Demo1234!`**:

| Correo | Roles |
|---|---|
| `cliente@demo.com` | cliente |
| `mesero@demo.com` | mesero |
| `cocinero@demo.com` | cocinero |
| `despachador@demo.com` | despachador |
| `supervisor@demo.com` | supervisor |
| `admin@demo.com` | admin |
| `multi@demo.com` | mesero + despachador |

---

## 4. Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `PORT` | `8002` | Puerto HTTP |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/panaderia` | JDBC de PostgreSQL |
| `DATABASE_USER` | `panaderia` | Usuario de base de datos |
| `DATABASE_PASSWORD` | `panaderia` | Contraseña de base de datos |
| `REDIS_URL` | `redis://localhost:6379/0` | Caché de refresh tokens (opcional) |
| `JWT_ISSUER` | `identity-service` | Claim `iss` |
| `JWT_AUDIENCE` | `panaderia-express` | Claim `aud` |
| `JWT_ACCESS_TTL_SECONDS` | `900` | Vida del access token |
| `JWT_REFRESH_TTL_SECONDS` | `604800` | Vida del refresh token |
| `JWT_PRIVATE_KEY_PATH` | `/run/secrets/jwt_private.pem` | Clave privada RS256 |
| `JWT_PUBLIC_KEY_PATH` | `/run/secrets/jwt_public.pem` | Clave pública RS256 |

---

## 5. Migraciones

Flyway se ejecuta al arrancar y crea el esquema `auth`:

- `V1__init_auth.sql`: `users`, `roles`, `permissions`, `user_roles`,
  `role_permissions`, `refresh_tokens`, `audit_log` e índices.
- `V2__seed_auth.sql`: roles, permisos, matriz de autorización y usuarios demo.

`spring.jpa.hibernate.ddl-auto=validate` garantiza que las entidades coincidan
con el esquema migrado.

---

## 6. Pruebas

Las pruebas de integración usan H2 en modo PostgreSQL, Flyway deshabilitado y
Redis apuntando a un puerto sin servicio; así no dependen de infraestructura.

```bash
# Con Gradle y JDK 21 instalados
gradle test --no-daemon

# Sin instalar Java/Gradle (Docker)
docker run --rm \
  -v "$PWD:/app" \
  -v panaderia-gradle-cache:/home/gradle/.gradle \
  -w /app gradle:8.10-jdk21 gradle test --no-daemon
```

Cobertura funcional:

- Registro público siempre como `cliente`.
- Login válido, credenciales inválidas y cuenta desactivada.
- Refresh con rotación y revocación.
- Claims, expiración y validación del JWT.
- Multi-rol: unión de permisos y nivel máximo.
- Denegaciones 401/403 y reglas de supervisor.
- Auditoría de login, logout y acciones administrativas.

---

## 7. Formato de errores

Todas las respuestas de error siguen el contrato del equipo:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Correo o contraseña incorrectos",
  "request_id": "c2f1...",
  "details": {}
}
```

| HTTP | Códigos típicos |
|---:|---|
| 400 | `VALIDATION_ERROR` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`, `INVALID_REFRESH_TOKEN` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 409 | `EMAIL_ALREADY_EXISTS`, `INVALID_STATE` |
| 500 | `INTERNAL_ERROR` |
