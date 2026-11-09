# API Gateway

Entrada única del sistema (Go, puerto `8080`). Enruta por prefijo hacia Identity, Order, Kitchen y Reporting, y es el único servicio de lógica publicado al host. Los contratos están en [`docs/equipo/contratos.md`](../docs/equipo/contratos.md).

> **Estado:** completo (etapa 2): ruteo, JWT RS256 con JWKS, permisos por prefijo, CORS, rate limiting, encabezados de identidad y errores uniformes.

## Tabla de rutas

Cada prefijo se reenvía sin reescribir la ruta ni la consulta. "Permisos" es general: basta tener uno; cada servicio vuelve a validar permiso y alcance.

| Prefijo | Destino | Permisos aceptados |
|---|---|---|
| `POST /auth/login`, `/auth/register`, `/auth/refresh` | Identity | Público |
| `/auth/*` (resto) | Identity | Autenticado |
| `/users/*`, `/roles/*` | Identity | `users:manage`, `users:manage_operational`, `roles:manage` |
| `/permissions/*` | Identity | `roles:manage` |
| `/audit/*` | Identity | `users:manage` |
| `/menu/*` | Order | Autenticado |
| `/orders/*` | Order | `orders:create_own`, `orders:read_own`, `orders:create`, `orders:charge`, `orders:read_all` |
| `/reservations/*` | Order | `reservations:manage_own`, `reservations:manage_all` |
| `GET /products/*` | Order | Autenticado |
| `POST/PUT/PATCH/DELETE /products/*` | Order | `products:manage` |
| `/queue/*` | Kitchen | `kitchen:read`, `kitchen:update`, `kitchen:deliver` |
| `/history/*` | Reporting | `history:read_own`, `history:read_all` (Reporting filtra por dueño) |
| `/reports/*` | Reporting | `reports:read` |

Reglas especiales:

- `POST /queue/orders` **no es alcanzable desde afuera**: el Gateway responde `403 FORBIDDEN`. Solo lo llama el Worker, directo a Kitchen, con `X-Service-Token`.
- El JWKS de Identity (`/.well-known/jwks.json`) no se expone; cualquier prefijo desconocido responde `404 NOT_FOUND`.
- `/permissions` y `/audit` y la regla por método de `/products` son decisiones propias, anotadas en [`axel-notas-integracion.md`](../docs/equipo/axel-notas-integracion.md).

## Autenticación y permisos

Orden de los middlewares: request id → logging → CORS → rate limit (IP) → auth → rate limit (usuario) → permisos → proxy.

- **JWT:** RS256 contra el JWKS de `JWKS_URL`, con `iss`, `aud` y `exp` obligatorios (5 s de tolerancia). Se rechazan otros algoritmos (`none`, HS256), `sub` que no sea UUID y claims con comas o caracteres de control. Sin token, inválido o vencido: `401 UNAUTHENTICATED`.
- **JWKS:** se cachea 10 min, se renueva ante un `kid` desconocido (máximo una consulta cada 10 s) y, si Identity no responde, se sigue usando la clave en caché. Sin caché ni Identity: `503 SERVICE_UNAVAILABLE`.
- **Permisos:** el Gateway aplica la tabla de arriba (basta uno); permiso ausente: `403 FORBIDDEN`. Cada servicio revalida permiso y alcance.
- **CORS:** solo los orígenes de `CORS_ORIGINS`. El KDS de escritorio y el Web SSR (llamadas desde el servidor) no envían `Origin` y no lo necesitan. Para el KDS en Chrome agregue su origen, por ejemplo `http://localhost:5000` con `flutter run -d chrome --web-port=5000`.
- **Rate limiting:** ventana fija de 1 min en Redis, por IP (antes de auth) y por usuario (después). Al superarlo: `429 RATE_LIMITED` con `Retry-After`. Si Redis no responde el límite deja de aplicarse (la petición pasa; cada intento espera como máximo 300 ms). `/health` y `/ready` no tienen límite. La IP es la del peer directo; no se confía en `X-Forwarded-For`, así que el Web SSR (una sola IP) comparte el límite por IP: el límite por usuario es el que protege a cada persona.

## Encabezados

El Gateway elimina de toda petición entrante `X-User-Id`, `X-User-Email`, `X-Roles`, `X-Permissions`, `X-Level` y `X-Service-Token`, y reemplaza `X-Request-Id` por uno propio (UUID). Con un JWT válido escribe:

| Encabezado | Origen |
|---|---|
| `X-User-Id` | `sub` |
| `X-User-Email` | `email` |
| `X-Roles` | `roles` (CSV) |
| `X-Permissions` | `permissions` (CSV) |
| `X-Level` | `level` |

Nunca reescribe el cuerpo ni `customer_id`. `Authorization` se conserva porque Identity lo necesita en `/auth/me` y `/auth/logout`.

## Errores

Todos los errores, incluidos los del proxy, usan `{"code","message","request_id","details":{}}`.

| HTTP | `code` | Cuándo |
|---|---|---|
| 401 | `UNAUTHENTICATED` | Sin token, inválido o vencido |
| 403 | `FORBIDDEN` | Permiso insuficiente o ruta bloqueada |
| 404 | `NOT_FOUND` | Prefijo desconocido |
| 429 | `RATE_LIMITED` | Límite superado |
| 502 | `BAD_GATEWAY` | El servicio respondió mal o tardó demasiado |
| 503 | `SERVICE_UNAVAILABLE` | No se pudo conectar con el servicio o verificar el JWKS |

## Salud

- `GET /health` → `200 {"status":"ok"}`.
- `GET /ready` → `200 {"status":"ready"}` o `503 {"status":"not_ready","dependencies":{"redis":"down"}}`. Verifica Redis.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `PORT` | No (`8080`) | Puerto de escucha |
| `IDENTITY_URL`, `ORDER_URL`, `KITCHEN_URL`, `REPORTING_URL` | Sí | URLs internas de los servicios |
| `REDIS_URL` | Sí | Redis (rate limiting) |
| `JWKS_URL` | Sí | JWKS de Identity |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Sí | `iss` y `aud` esperados |
| `SERVICE_TOKEN` | No | Secreto compartido del prototipo |
| `CORS_ORIGINS` | No | Orígenes permitidos, separados por coma |
| `RATE_LIMIT_USER_PER_MINUTE` | No (`120`) | Límite por usuario |
| `RATE_LIMIT_IP_PER_MINUTE` | No (`300`) | Límite por IP |

Ver [`.env.example`](.env.example).

## Correr y probar

```powershell
cd api-gateway
go vet ./...
go test ./...
go build ./...
```

Con Docker (solo Gateway y sus dependencias; Identity, Order y Reporting aún no existen):

```powershell
docker compose -f docker-compose.p2.yml --env-file .env.p2.example up -d --build redis kitchen-service
docker compose -f docker-compose.p2.yml --env-file .env.p2.example up -d --no-deps --build api-gateway
curl http://localhost:8080/health
```

## Estructura

```text
cmd/gateway/main.go           arranque
internal/config               variables de entorno
internal/router               tabla de rutas (routes.go) y handler (router.go)
internal/jwks                 caché de claves públicas
internal/proxy                reverse proxy y limpieza de encabezados
internal/middleware           request id, logging, CORS, rate limit, auth y permisos
internal/http                 formato uniforme de errores
tests/                        pruebas con httptest, miniredis y un JWKS falso
tests/jwtkit                 helper de pruebas: sirve un JWKS falso y firma tokens (no va en la imagen)
```
