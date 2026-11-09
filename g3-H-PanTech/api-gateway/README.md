# API Gateway

Entrada única del sistema (Go, puerto `8080`). Enruta por prefijo hacia Identity, Order, Kitchen y Reporting, y es el único servicio de lógica publicado al host. Los contratos están en [`docs/equipo/contratos.md`](../docs/equipo/contratos.md).

> **Estado:** etapa 1 (ruteo, salud, bloqueo de `POST /queue/orders`, eliminación de encabezados de identidad y errores uniformes). La validación de JWT, los permisos, CORS y el rate limiting llegan en la etapa 2: hasta entonces el Gateway **no autentica** y no debe exponerse fuera de pruebas.

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

## Encabezados

El Gateway elimina de toda petición entrante `X-User-Id`, `X-User-Email`, `X-Roles`, `X-Permissions`, `X-Level` y `X-Service-Token`, y reemplaza `X-Request-Id` por uno propio (UUID). En la etapa 2 escribirá los `X-*` de identidad a partir del JWT validado y nunca reescribirá `customer_id`. `Authorization` se conserva porque Identity lo necesita en `/auth/me` y `/auth/logout`.

## Errores

Todos los errores, incluidos los del proxy, usan `{"code","message","request_id","details":{}}`.

| HTTP | `code` | Cuándo |
|---|---|---|
| 401 | `UNAUTHENTICATED` | Sin token o vencido (etapa 2) |
| 403 | `FORBIDDEN` | Permiso insuficiente o ruta bloqueada |
| 404 | `NOT_FOUND` | Prefijo desconocido |
| 429 | `RATE_LIMITED` | Límite superado (etapa 2) |
| 502 | `BAD_GATEWAY` | El servicio respondió mal o tardó demasiado |
| 503 | `SERVICE_UNAVAILABLE` | No se pudo conectar con el servicio |

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
internal/proxy                reverse proxy y limpieza de encabezados
internal/middleware           request id, logging y recover
internal/http                 formato uniforme de errores
tests/                        pruebas con httptest y miniredis
```
