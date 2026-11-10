# Alineación de order-service, worker y reporting-service con la infraestructura de Jaime

Revisado contra `docker-compose.p2.yml`, `.env.p2.example` y `infrastructure/`. **Corregido en mi código** (ya no hay que tocar nada de Jaime para esto):

| # | Diferencia encontrada | Corrección |
|---|---|---|
| 1 | El compose entrega `DATABASE_URL=postgresql+psycopg2://...`; yo usaba el driver psycopg 3 | Order Service normaliza `postgresql+psycopg2://` a `postgresql+psycopg://` |
| 2 | Mongo llega como `MONGODB_URL` con usuario y clave (`authSource=admin`); yo leía `MONGO_URL` sin autenticación | Los tres servicios leen `MONGODB_URL` y toman la base de la ruta de la URL |
| 3 | Las colas son **una por evento** (`worker.order_paid`, `worker.order_ready`, `worker.reservation_created`, `worker.reservation_cancelled`) con DLX `panaderia.dlx`; yo consumía una cola propia `worker.events` | El Worker consume esas cuatro colas; `DECLARE_TOPOLOGY=false` por defecto (las crea `rabbitmq-init`) |
| 4 | La DLQ recibe por la DLX `panaderia.dlx` con routing key `panaderia.dlq` | El Worker publica los fallidos en esa DLX |
| 5 | Object storage es **RustFS con variables `S3_*`** (`S3_ENDPOINT=http://rustfs:9000`), no MinIO | Worker y Reporting usan `S3_*` (el SDK `minio` habla S3) y aceptan el endpoint con esquema |
| 6 | El JWT lleva `aud=panaderia-express` y PyJWT rechaza un token con `aud` si no se indica audiencia | Order Service valida `JWT_AUDIENCE`; Reporting valida `Jwt:Audience` |
| 7 | El documento de `menu` usa `available` (índice `category + available`); yo usaba `active` | La API sigue diciendo `active`; en Mongo se guarda `available` |
| 8 | `PORT` (de `ORDER_PORT`, `REPORTING_PORT`) | Entrypoint y healthcheck de Order Service usan `PORT`; Reporting escucha en 8003 |
| 9 | `reservation.confirmed` **no tiene cola enlazada**: con `mandatory=True`, publicarlo habría bloqueado todo el outbox | Por defecto **no se publica** (`PUBLISH_RESERVATION_CONFIRMED=false`) |
| 10 | `order_history` indexa `created_at` | El Worker guarda `created_at` (= `paid_at`, porque el evento no trae la fecha de creación) |
| 11 | Reporting recibe `DATABASE_URL` en formato .NET sin modo solo lectura | Reporting le agrega `default_transaction_read_only=on` si falta |

## Lo que sí debe cambiar Jaime (o el dueño de cada archivo)

1. **`docker-compose.p2.yml`, servicio `worker`:** agregar `ORDER_SERVICE_URL: ${ORDER_URL:-http://order-service:8000}` y `order-service: condition: service_healthy` en `depends_on`. (Funciona con el valor por defecto del código, pero conviene que sea explícito.)
2. **Servicios `order-service` y `reporting-service`:** agregar `JWKS_URL`, `JWT_ISSUER` y `JWT_AUDIENCE` (ya están en `.env.p2.example`); el gateway sí los recibe, estos dos no.
3. **URLs firmadas de los reportes (resuelto):** Compose publica RustFS `:9000` para S3 y configura `S3_PUBLIC_ENDPOINT=http://localhost:9000`, de modo que el navegador pueda abrir las exportaciones.
4. **Cola de `reservation.confirmed` (opcional):** ver `rabbitmq.reservation_confirmed.parche.json`. Sin ella, el read model `reservation_read` queda en `pendiente` después de confirmar.
5. **Variables nuevas para `.env.p2.example`:** `PUBLISH_RESERVATION_CONFIRMED=false` (Order Service) y `S3_PUBLIC_ENDPOINT` (Reporting).
6. **Mongo:** no hay que cambiar `init.js`. El `menu` acepta mis documentos (`price` decimal, `category`, `available`).

## Cosas que dependen de otros compañeros
- `docker-compose.p2.yml` construye `web-ssr`, `api-gateway`, `identity-service` y `kitchen-service`, que **aún no están en el repo**: el compose completo no arranca hasta que existan. Mientras tanto, `verificacion/docker-compose.demo.yml` levanta mis tres servicios sobre la infraestructura real de Jaime, con una cocina simulada.
- Axel: `kitchen.order_status_events(order_id, status, changed_at)` y el endpoint `POST /queue/orders` (ver `contratos-yeison.md`).
- Diego: confirmar `aud=panaderia-express`, `orders:read_all` y el formato del claim `sub`.
