# Plan de trabajo — Diego Fernando Waitoto Quintero

## Rol

**Identidad y experiencia web.** Soy el dueño del Identity Service y de la Web SSR. Mi trabajo une la autenticación, los roles, los permisos y toda la interfaz web que usan el cliente y el personal.

| Campo | Valor |
|---|---|
| Rama personal | `g3-H/diego-identidad-web` |
| Rama de integración | `g3-H/Pantech-P2` |
| Componentes propios | `identity-service/`, `web-ssr/` |
| Lenguajes | Kotlin (Identity), TypeScript (Web SSR) |
| Depende de | Jaime (PostgreSQL, Gateway, contratos, JWT keys), Yeison (Order y Reporting APIs), Axel (Kitchen API) |
| Bloquea a | Todos: sin login y JWT no hay flujo completo |

## Misión

Que cada persona pueda iniciar sesión con su cuenta, ver solo lo que le corresponde y usar la Web SSR sin conocer la topología interna. La Web SSR debe renderizar en el servidor, hablar con el API Gateway y nunca guardar el token en `localStorage`.

## Responsabilidades

1. **Identity Service:** usuarios, credenciales, roles, permisos, jerarquía, auditoría y emisión de JWT.
2. **Web SSR:** login, registro, tienda del cliente, backoffice del personal y rutas protegidas.
3. **Autorización de interfaz:** mostrar u ocultar vistas y botones según permisos, sin confiar solo en el frontend.
4. **Documentación:** flujo de autenticación, claims, matriz de permisos y diagramas de login.

## Archivos que creo y modifico

### `identity-service/` (crear)

| Archivo | Contenido |
|---|---|
| `identity-service/build.gradle.kts` | Dependencias Spring Boot, JPA, Flyway y JWT |
| `identity-service/settings.gradle.kts` | Nombre del proyecto |
| `identity-service/Dockerfile` | Build multistage con JRE |
| `identity-service/.dockerignore` | Excluir `build/`, tests y cachés |
| `identity-service/src/main/resources/application.yml` | Puerto 8002, datasource, Flyway y claves JWT |
| `identity-service/src/main/resources/db/migration/V1__init_auth.sql` | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `audit_log` |
| `identity-service/src/main/resources/db/migration/V2__seed_auth.sql` | Roles, permisos y usuarios demo |
| `identity-service/src/main/kotlin/.../IdentityApplication.kt` | Punto de entrada |
| `identity-service/src/main/kotlin/.../config/SecurityConfig.kt` | Spring Security y rutas públicas |
| `identity-service/src/main/kotlin/.../config/JwtConfig.kt` | Claves RS256 y JWKS |
| `identity-service/src/main/kotlin/.../auth/AuthController.kt` | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me` |
| `identity-service/src/main/kotlin/.../auth/AuthService.kt` | Registro, login, refresh y logout |
| `identity-service/src/main/kotlin/.../auth/JwtService.kt` | Emisión, firma y expiración |
| `identity-service/src/main/kotlin/.../users/UserController.kt` | `/users/*` |
| `identity-service/src/main/kotlin/.../users/UserService.kt` | Alta, baja lógica y asignación multi-rol |
| `identity-service/src/main/kotlin/.../roles/RoleController.kt` | `/roles`, `/permissions` |
| `identity-service/src/main/kotlin/.../audit/AuditService.kt` | Registro de acciones sensibles |
| `identity-service/src/main/kotlin/.../domain/User.kt`, `Role.kt`, `Permission.kt` | Entidades |
| `identity-service/src/main/kotlin/.../repository/*.kt` | Repositorios JPA |
| `identity-service/src/test/kotlin/...` | Registro, login, expiración, multi-rol y denegaciones |
| `identity-service/README.md` | Endpoints, variables y modelo de autorización |

### `web-ssr/` (crear)

| Archivo | Contenido |
|---|---|
| `web-ssr/package.json` | Next.js, React, TypeScript y Playwright |
| `web-ssr/next.config.mjs` | `output: standalone` para el contenedor |
| `web-ssr/tsconfig.json` | Configuración estricta |
| `web-ssr/middleware.ts` | Protección de rutas y redirección por rol |
| `web-ssr/Dockerfile` | Build y `next start` |
| `web-ssr/.dockerignore` | Excluir `.next/`, `node_modules/` |
| `web-ssr/.env.example` | `API_GATEWAY_URL`, `JWT_COOKIE_NAME` |
| `web-ssr/app/layout.tsx` | Shell SSR |
| `web-ssr/app/page.tsx` | Portada y menú |
| `web-ssr/app/globals.css` | Estilos base |
| `web-ssr/app/(auth)/login/page.tsx` | Login |
| `web-ssr/app/(auth)/register/page.tsx` | Registro público |
| `web-ssr/app/(shop)/menu/page.tsx` | Menú |
| `web-ssr/app/(shop)/cart/page.tsx` | Carrito |
| `web-ssr/app/(shop)/checkout/page.tsx` | Checkout y pago simulado |
| `web-ssr/app/(shop)/orders/page.tsx` | Pedidos propios |
| `web-ssr/app/(shop)/orders/[id]/page.tsx` | Seguimiento con polling |
| `web-ssr/app/(shop)/reservations/page.tsx` | Reservas propias |
| `web-ssr/app/(staff)/staff/orders/page.tsx` | Vista de mesero |
| `web-ssr/app/(staff)/staff/reservations/page.tsx` | Reservas del personal |
| `web-ssr/app/(staff)/staff/history/page.tsx` | Historial general |
| `web-ssr/app/(staff)/staff/products/page.tsx` | CRUD de productos |
| `web-ssr/app/(staff)/staff/users/page.tsx` | Cuentas de empleados |
| `web-ssr/app/(staff)/staff/roles/page.tsx` | Roles y permisos |
| `web-ssr/app/(staff)/staff/reports/page.tsx` | Reportes |
| `web-ssr/app/(staff)/staff/kitchen/page.tsx` | Enlace o vista de solo lectura del KDS |
| `web-ssr/components/*` | Tablas, formularios, navegación y estados |
| `web-ssr/lib/api.ts` | Cliente del Gateway con cookie |
| `web-ssr/lib/auth.ts` | Lectura y renovación de sesión |
| `web-ssr/lib/permissions.ts` | `can(permission)` |
| `web-ssr/tests/e2e/*.spec.ts` | Login, pedido, reserva y denegaciones |
| `web-ssr/playwright.config.ts` | Configuración E2E |
| `web-ssr/README.md` | Rutas, permisos y variables |

### Archivos compartidos y quién más puede modificarlos

| Archivo | Acción permitida | Quién más lo modifica | Regla |
|---|---|---|---|
| `docker-compose.p2.yml` | Agregar o ajustar los bloques `identity-service` y `web-ssr` | Jaime (dueño), Yeison y Axel (sus bloques) | Solo mi bloque; Jaime revisa y mergea |
| `.env.p2.example` | Proponer `IDENTITY_*` y `WEB_SSR_*` | Jaime (dueño), Yeison y Axel | Jaime consolida |
| `docs/equipo/contratos.md` | Proponer claims, JWKS y cookies | Jaime, Yeison y Axel | Jaime consolida |
| `docs/secuencia_login.puml` | Crear y mantener el `.puml` | Jaime (renderiza y sube el `.png`) | Avisar a Jaime para que agregue el PNG al README |
| `README.md` | No editar | Jaime | Mi documentación va en `identity-service/README.md` y `web-ssr/README.md` |
| `docs/Vista CyC P2.puml`, `docs/tiers_despliegue P2.puml`, `docs/capas P2.puml`, `docs/descomposicion P2.puml` | No editar | Jaime | Propongo cambios por comentario en el PR |
| `infrastructure/postgres/init.sql` | No editar | Jaime | Mi esquema va en las migraciones Flyway |
| `infrastructure/minio/init.sh` | No editar | Jaime | No uso MinIO directamente |
| `scripts/smoke_test.py` | No editar | Jaime | Agrego mis pruebas en `identity-service/` y `web-ssr/tests/` |

### Archivos que no debo tocar

- `order-service/`, `worker/`, `reporting-service/`, `api-gateway/`, `kitchen-service/` y `kds-pos/`.
- `infrastructure/rabbitmq/definitions.json` y `infrastructure/redis/redis.conf`.
- Los otros tres planes de `docs/equipo/`.

## Fases y tareas

### Fase 0 — Esqueleto y contratos (primera semana)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| D0.1 | Crear `identity-service/` con Spring Boot y Gradle | Proyecto que compila y arranca | `GET /health` responde 200 |
| D0.2 | Crear el `Dockerfile` de Identity | Imagen multistage | Corre con variables de entorno, sin secretos |
| D0.3 | Crear `web-ssr/` con Next.js y TypeScript | Proyecto que compila y renderiza | `GET /` devuelve HTML renderizado en servidor |
| D0.4 | Crear el `Dockerfile` de Web SSR | Imagen Node en modo producción | `next start` funciona dentro del contenedor |
| D0.5 | Acordar claims y JWKS con Jaime | Documento o PR | Gateway y servicios pueden validar el token |
| D0.6 | Definir el mapa de rutas y permisos | Tabla en `web-ssr/README.md` | Cada ruta tiene permiso y rol mínimo |

### Fase 1 — Identity Service (semanas 2–3)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| D1.1 | Migraciones Flyway del esquema `auth` | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `audit_log` | `flyway migrate` deja el esquema listo |
| D1.2 | Datos semilla | Roles, permisos y usuarios demo | Existe un usuario por rol y un usuario multi-rol |
| D1.3 | Registro público | `POST /auth/register` | Siempre crea `cliente`; no acepta roles del cuerpo |
| D1.4 | Login y refresh | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` | Access token de 15 min; refresh de 7 días |
| D1.5 | JWT RS256 y JWKS | `GET /.well-known/jwks.json` | Claims `sub`, `email`, `roles`, `permissions`, `level`, `iss`, `iat`, `exp` |
| D1.6 | Consulta de sesión | `GET /auth/me` | Devuelve usuario, roles, permisos y nivel |
| D1.7 | Administración de usuarios | `GET/POST/PATCH /users`, `POST /users/{id}/roles`, `DELETE /users/{id}/roles/{role}` | Admin gestiona todo; supervisor solo cuentas operativas |
| D1.8 | Roles y permisos | `GET /roles`, `GET /permissions` | El admin puede consultar la matriz |
| D1.9 | Auditoría | `GET /audit` y registro automático | Quién creó, desactivó o asignó un rol queda registrado |
| D1.10 | Pruebas | Unitarias e integración | Registro, login, expiración, multi-rol y denegaciones |

**Reglas de negocio que debo respetar:**

- El registro público **solo** crea `cliente`.
- Un `admin` crea cuentas de personal y asigna roles.
- Un `supervisor` puede gestionar cuentas operativas si tiene `users:manage_operational`, pero no puede crear `admin` ni modificar roles.
- Las cuentas no se eliminan: se desactivan.
- Los permisos efectivos son la unión de los roles; el nivel efectivo es el mayor.
- `productos` e `historial` son permisos, no roles.

### Fase 2 — Web SSR: autenticación y tienda del cliente (semanas 3–4)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| D2.1 | Layout, diseño y componentes base | Shell SSR, navegación y estados de error | Se ve bien en escritorio y móvil |
| D2.2 | Login y registro | `/login`, `/register` | Sesión con cookie `httpOnly`; redirección por rol |
| D2.3 | Middleware de rutas | `middleware.ts` | Sin sesión redirige a login; sin permiso devuelve 403 |
| D2.4 | Cliente API del servidor | `lib/api.ts` | Las llamadas del servidor llevan el JWT al Gateway |
| D2.5 | Menú y catálogo | `/` y `/menu` | Renderizado en servidor; precios y tiempos visibles |
| D2.6 | Carrito y checkout | `/cart`, `/checkout` | Total calculado en el servidor; no se confía en el cliente |
| D2.7 | Pago simulado | `/checkout/payment` | Muestra el total del servidor y confirma el pago |
| D2.8 | Seguimiento del pedido | `/orders/[id]` | Polling cada 3–5 s; solo el dueño o personal autorizado |
| D2.9 | Historial propio | `/orders` | El cliente solo ve sus pedidos |
| D2.10 | Reservas | `/reservations` | Crear, listar y cancelar solo las propias |

### Fase 3 — Web SSR: backoffice del personal (semanas 4–5)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| D3.1 | Vista de mesero | `/staff/orders`, `/staff/reservations` | Crear pedidos, cobrar y gestionar reservas |
| D3.2 | Vista de supervisor | `/staff/history`, `/staff/cancel` | Historial general, cancelación y cuentas operativas |
| D3.3 | Vista de admin: productos | `/staff/products` | CRUD de productos y minutos de preparación |
| D3.4 | Vista de admin: usuarios y roles | `/staff/users`, `/staff/roles` | Crear cuentas, asignar multi-rol, desactivar |
| D3.5 | Vista de reportes | `/staff/reports` | Consume Reporting Service; filtros por fecha |
| D3.6 | Enlace a KDS/POS | `/staff/kitchen` | Muestra la cola en modo lectura o abre el KDS |
| D3.7 | Permisos en la interfaz | `can('orders:charge')` | Los botones se ocultan, pero el backend sigue validando |

### Fase 4 — Pruebas, documentación e integración (semana 6)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| D4.1 | Pruebas E2E con Playwright | Login, pedido, reserva, denegaciones | Corren contra el compose completo |
| D4.2 | Prueba de SSR | `view-source` o test de HTML | El menú aparece en el HTML inicial |
| D4.3 | Pruebas de seguridad | Cookies, expiración, 401/403 | No hay token en `localStorage` |
| D4.4 | Documentación | `identity-service/README.md`, `web-ssr/README.md` | Endpoints, variables y rutas documentados |
| D4.5 | Diagramas | Secuencia de login y C&C actualizados | Coinciden con el código |
| D4.6 | Integración con Jaime | PR revisado y smoke test | El flujo completo pasa |

## Rutas y permisos de la Web SSR

| Ruta | Quién | Permiso |
|---|---|---|
| `/` | Público | Ver menú |
| `/login`, `/register` | Público | — |
| `/cart`, `/checkout` | Cliente | `orders:create_own` |
| `/orders`, `/orders/[id]` | Cliente | `orders:read_own` |
| `/reservations` | Cliente | `reservations:manage_own` |
| `/staff/orders` | Mesero, supervisor, admin | `orders:create`, `orders:charge` |
| `/staff/reservations` | Mesero, supervisor, admin | `reservations:manage_all` |
| `/staff/history` | Supervisor, admin | `history:read_all` |
| `/staff/products` | Admin | `products:manage` |
| `/staff/users` | Admin / supervisor | `users:manage`, `users:manage_operational` |
| `/staff/roles` | Admin | `roles:manage` |
| `/staff/reports` | Supervisor, admin | `reports:read` |

## Contratos que debo respetar

### JWT

- Algoritmo RS256, emisor `identity-service`.
- Claims: `sub`, `email`, `roles`, `permissions`, `level`, `iss`, `iat`, `exp`.
- El Gateway valida y propaga `X-User-Id`, `X-Roles`, `X-Permissions` y `X-Level`.
- Los servicios vuelven a validar permisos y alcance.

### Cookies de sesión

- Access token en cookie `httpOnly`, `sameSite=lax`, `secure` en producción.
- Refresh token en cookie `httpOnly` separada.
- Nunca guardar tokens en `localStorage` ni en el HTML.
- El servidor de Next.js lee la cookie y llama al Gateway.

## Dependencias

| Dependo de | Para |
|---|---|
| Jaime | PostgreSQL, Gateway, variables de entorno, JWKS y compose |
| Yeison | `GET /menu`, `POST /orders`, `POST /orders/{id}/payment`, reservas, `GET /history` y `GET /reports` |
| Axel | `GET /queue` para la vista de cocina |

| Dependen de mí | Para |
|---|---|
| Jaime | Login y JWT para el smoke test |
| Yeison | `customer_id` y permisos en las peticiones |
| Axel | Validación de JWT en Gateway y rutas del KDS |

## Definición de terminado

- Identity compila, migra y pasa sus pruebas.
- Todos los roles pueden iniciar sesión.
- El cliente solo ve sus pedidos y reservas.
- El personal ve las vistas según su rol.
- La Web SSR renderiza en servidor y no filtra el token.
- Las rutas protegidas devuelven 401/403 correctamente.
- El PR tiene pruebas, README y diagramas actualizados.

## Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| La Web SSR es el componente más grande | Priorizar login, menú, pedido y seguimiento; dejar admin CRUD para el final |
| Kotlin/Spring Boot es nuevo para el equipo | Usar Spring Initializr, mantener el diseño simple y apoyarse en tests |
| Conflictos entre SSR y componentes cliente | Separar `app/(shop)` y `app/(staff)`; componentes de servidor por defecto |
| Cookies y CORS mal configurados | Probar login desde el navegador y desde el servidor; coordinar con Jaime |
| Cambios de contrato con Order/Kitchen | Revisar OpenAPI antes de implementar la UI |

## Primer PR

**`g3-H/diego-identidad-web`:** Identity Service con migraciones, seeds, registro, login, JWT y JWKS; Web SSR con layout, `/login`, `/register` y middleware de rutas. Sin tienda ni backoffice todavía.

## Lo que no hago

- No implemento la lógica de pedidos, pagos, cola o reportes; solo consumo sus APIs.
- No expongo los servicios internos al navegador; todo pasa por el Gateway.
- No valido permisos solo en el frontend.
