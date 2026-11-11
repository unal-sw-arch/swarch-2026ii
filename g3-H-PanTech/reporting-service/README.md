# Reporting Service

Historial de pedidos, reportes y exportaciones de Panadería Express. C# / .NET 8 (ASP.NET Core con controladores), puerto **8003**.
Servicio de **solo lectura**: no escribe en las tablas del Order Service ni en los read models.
Los usuarios llegan por el API Gateway (`/history/*`, `/reports/*`).

OpenAPI: `http://reporting-service:8003/docs` (JSON en `/swagger/v1/swagger.json`).

## Endpoints

| Método | Ruta | Permiso | Notas |
|---|---|---|---|
| GET | `/health` | — | Comprueba PostgreSQL |
| GET | `/history` | `history:read_own` / `history:read_all` | El cliente solo ve lo propio (ignora `customer_id`); supervisor y admin ven todo. `?from=&to=&status=&customer_id=&page=&page_size=` (máx. 100) |
| GET | `/history/orders/{id}` | dueño o `history:read_all` | Detalle con ítems y línea de tiempo; pedido ajeno → 404 |
| GET | `/reports/sales` | `reports:read` | Ventas por día, ticket promedio y top 10 productos. `?from=&to=` (YYYY-MM-DD, inclusivo; por defecto 30 días) |
| GET | `/reports/times` | `reports:read` | Tiempos promedio de preparación y entrega |
| POST | `/reports/export` | `reports:read` | `{"report": "sales\|times\|history", "format": "csv\|pdf", "from"?, "to"?}` → sube a RustFS y devuelve `{url, object, ...}` (URL firmada) |

Errores: `{"code", "message", "request_id", "details"?}`; JSON en `snake_case`. Se propaga `X-Request-Id`.
Las fechas se interpretan en `Reporting:BusinessTimeZone` (por defecto `America/Bogota`); el rango máximo es `Reporting:MaxRangeDays`.

## De dónde salen los datos

| Consulta | Fuente |
|---|---|
| `/history` y detalle | MongoDB `order_history` (read model del Worker); si el detalle aún no está, se lee de PostgreSQL `orders.*` |
| `/reports/sales` | PostgreSQL `orders.payments`, `orders.orders`, `orders.order_items` (pedidos pagados y no cancelados) |
| `/reports/times` | PostgreSQL `kitchen.order_status_events` (`order_id`, `status`, `changed_at`) — **supuesto sobre el esquema de Kitchen**; si la tabla no existe responde 503 |
| Exportaciones | RustFS/S3, bucket `reports`, objeto `exports/{fecha}/{reporte}-{rango}-{guid}.{csv\|pdf}` |

La cadena de conexión por defecto incluye `Options=-c default_transaction_read_only=on`: PostgreSQL rechaza cualquier escritura.
Los CSV neutralizan celdas que empiezan por `= + - @` (inyección de fórmulas).

## Autenticación

JWT RS256 de identity-service. Las claves salen de `Jwt:JwksUrl` (cacheadas 5 min; se refrescan si llega un `kid` nuevo) o de
`Jwt:PublicKeyPem` si se define. Se valida firma, expiración y `Jwt:Issuer`. Permisos desde el claim `permissions`.

## Variables de entorno

Mismos nombres que `docker-compose.p2.yml`: `DATABASE_URL` (formato .NET; se le agrega `default_transaction_read_only=on` si falta), `MONGODB_URL`,
`S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_BUCKET_REPORTS`, `S3_USE_SSL`, `JWKS_URL`, `JWT_ISSUER`, `JWT_AUDIENCE`, `PORT`.
Extras: `S3_PUBLIC_ENDPOINT` (host con el que se firma la URL para el navegador), `Jwt__PublicKeyFile` / `Jwt__PublicKeyPem` (desarrollo),
`Reporting__BusinessTimeZone`, `Reporting__MaxRangeDays`. Los valores por defecto están en `appsettings.json` (las variables de entorno tienen prioridad).

## Correr y probar

```bash
dotnet run --project ReportingService.csproj
dotnet test Tests/ReportingService.Tests.csproj   # permisos, filtros, rangos y exportación; sin infraestructura (repositorios falsos)

docker build -t reporting-service .
```
Las exportaciones PDF usan QuestPDF (licencia Community); en Linux necesitan `libfontconfig1` (ya está en el `Dockerfile`).
