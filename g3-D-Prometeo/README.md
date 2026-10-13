# Team 3-D-Prometeo

## Members

- Daniel Aguilar Castro
- Omar Chaparro Flechas
- Baruj Vladimir Ramirez Escalante
- David Alejandro Herrera Novoa

## Project
Sistema de operación y administración de restaurante. La aplicación usa una API desplegada en Cloudflare Workers, D1 como base de datos y un Durable Object para distribuir cambios en tiempo real.
## Architecture

## Component-and-connector (C&C Architechture)

### C&C View
![Components and conectors wiew](diagrams\Components-and-connectors-view.jpeg)

### Description of architectural styles used.
- **Cliente-servidor:** dos clientes Flutter (operativo y administrativo) consumen un único backend, `apps/cloud_api`. Los clientes no se comunican entre sí ni guardan datos compartidos; toda regla de negocio se valida en el servidor.
- **API REST sobre HTTPS:** el backend expone recursos versionados bajo `/api/v1` (`auth`, `admin`, pedidos, salones, catálogo) con verbos HTTP estándar. Las lecturas son `GET` y las escrituras son `POST`, `PUT`, `PATCH` y `DELETE` autenticadas con token de sesión.
- **Serverless / edge computing:** la API corre como Cloudflare Worker, sin servidores que administrar, y usa D1 (SQLite gestionado) como base de datos y un Durable Object para el estado con coordinación.
- **Publicación-suscripción orientada a eventos:** los clientes abren un WebSocket con el Durable Object `RestaurantCoordinator`. Cada mutación exitosa genera parches pequeños por recurso (salones, pedidos, actividad, métricas, catálogo) que se difunden (multicast) solo a los clientes suscritos al recurso afectado.
- **Separación de lecturas y escrituras (estilo CQRS):** las lecturas van directo del Worker a D1, y el catálogo se sirve desde la Cache API de Workers con llaves versionadas. Las escrituras se canalizan por el coordinador, que las serializa, las ejecuta y publica sus efectos.
- **Arquitectura por capas y por features:** en el backend, `features` (rutas y reglas por capacidad), `domain` (reglas propias del servidor), `infrastructure` (Durable Object y tiempo real) y `shared` (utilidades HTTP/D1). En los clientes, `features` (pantallas), `services` (acceso a la API, tiempo real y sesión) y `utils`.

### Description of architectural elements and relations.
## Elementos principales

*   **operational_client:** Aplicación Flutter para la operación diaria del restaurante: selección de salones, gestión de mesas, toma y entrega de pedidos, cobros, división de cuentas y ventas por turno.
*   **admin_client:** Aplicación Flutter para la administración del negocio: catálogo, ingredientes, salones, empleados, historial y métricas. Disponible solo para usuarios con rol de administrador.
*   **cloud_api:** Backend principal, implementado como Cloudflare Worker con Hono. Es el único punto de entrada público del sistema y concentra la lógica de negocio y la coordinación en tiempo real. Internamente se compone de:
    *   `index.ts`: router público que valida sesiones, aplica middleware y expone `/api/v1/*` y `/api/v1/realtime`.
    *   `features/*`: módulos de dominio (`auth`, `catalog`, `halls`, `orders`, `admin`, `read_model`).
    *   `mutationApp.ts`: router interno que registra solo las rutas de escritura.
    *   `RestaurantCoordinator`: Durable Object que serializa las mutaciones, valida sesión, rol y acceso por salón, garantiza idempotencia y difunde parches de tiempo real a los clientes suscritos.
*   **D1 Database:** Base de datos SQL y única fuente de verdad del sistema. Almacena usuarios, sesiones, catálogo, salones, órdenes, metadatos de idempotencia y versiones de recursos. Ningún cliente accede a ella directamente.

## Relaciones

*   **operational_client ↔ cloud_api:** HTTPS REST en `/api/v1/*` para consultas y operaciones, y WebSocket en `/api/v1/realtime` para recibir actualizaciones de mesas, pedidos y layout.
*   **admin_client ↔ cloud_api:** mismos canales que el cliente operativo, con acceso adicional a operaciones administrativas, métricas y al feed administrativo en tiempo real.
*   **cloud_api → D1:** SQL mediante prepared statements. Las lecturas se resuelven directamente en el Worker; las escrituras se ejecutan a través del `RestaurantCoordinator` y `mutationApp`. El Coordinator también consulta permisos y persiste los receipts de idempotencia.
*   **cloud_api → clientes:** tras cada mutación exitosa, el `RestaurantCoordinator` difunde parches (`RealtimePatch`) por la conexión WebSocket abierta. D1 no emite cambios.
```text
apps/
  cloud_api/             Worker, reglas del servidor y tiempo real
  operational_client/   Flutter para meseros, caja y operación del salón
  admin_client/         Flutter para configuración, métricas e historial
database/
  d1/migrations/        Esquema versionado de D1
  d1/seeds/             Datos de desarrollo
scripts/
  catalog/              Importadores de catálogos hacia D1
docs/
  architecture/         Diseño vigente del sistema
  development/          Reglas y flujos para contribuir
```

Los directorios nativos de Flutter (`android`, `ios`, `linux`, `macos`, `web` y `windows`) contienen integración de plataforma y, en gran parte, archivos generados. El código de producto vive en `lib`.

## How to run

### Clonar el repositorio

```bash
git clone https://github.com/unal-sw-arch/swarch-2026ii.git
cd swarch-2026ii/g3-D-Prometeo
```

Todos los comandos siguientes se ejecutan desde la carpeta `g3-D-Prometeo/`.

### Con Docker (De manera Local)

Requisitos: Docker y Docker Compose.

```bash
docker compose up --build
```

| Servicio          | Descripción                                               | URL                   |
| ----------------- | --------------------------------------------------------- | --------------------- |
| `backend`         | Worker en `wrangler dev` (D1 y Durable Object emulados)   | http://localhost:8787 |
| `web-operational` | Build web del cliente operativo servido por nginx         | http://localhost:8080 |
| `web-admin`       | Build web del cliente administrativo servido por nginx    | http://localhost:8081 |

Con el stack levantado, aplica las migraciones y los datos de desarrollo en otra terminal:

```bash
docker compose exec backend npm run cloud:d1:migrate:local
docker compose exec backend npm run cloud:d1:seed:local
```

Notas:

- El backend monta el código fuente para hot-reload y guarda el estado emulado de D1 en el volumen `wrangler-state`, que persiste entre reinicios. Para empezar desde cero: `docker compose down -v`.
- La URL del backend se compila dentro de los builds web (`--dart-define=RESTAURANT_API_URL`, por defecto `http://localhost:8787`). Si cambias el puerto o el host del backend, reconstruye las imágenes web con el build arg actualizado.
- La imagen de `web-admin` se construye con contexto `apps/` porque `admin_client` depende de `operational_client` por path.
- Docker solo se usa para desarrollo local; en producción el Worker se despliega a Cloudflare con `npm run cloud:deploy`.

### Sin Docker

#### Comandos principales

```bash
npm install
npm run cloud:typecheck
npm test
npm run cloud:dev
```

#### Clientes Flutter

Para cada cliente Flutter:

```bash
flutter pub get
flutter analyze
flutter test
```

Consulta [la arquitectura](docs/architecture/system.md) y [las reglas de contribución](CONTRIBUTING.md) antes de añadir una funcionalidad.
