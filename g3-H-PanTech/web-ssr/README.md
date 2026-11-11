# Web SSR — Panadería Express

Aplicación Next.js (App Router) con Server-Side Rendering para clientes y personal.
No es una SPA: las páginas se renderizan en el servidor y las mutaciones usan Server
Actions. El navegador nunca recibe el access token ni el refresh token; ambos viven
en cookies httpOnly.

## 1. Rutas

### Tienda del cliente

| Ruta | Descripción |
|---|---|
| `/` | Portada pública con estado del prototipo |
| `/menu` | Menú renderizado en servidor, con precios y tiempos |
| `/cart` | Carrito en cookie httpOnly; el total se calcula en servidor |
| `/checkout` | Confirmación del pedido y notas |
| `/checkout/payment?orderId=…` | Pago simulado con idempotencia |
| `/orders` | Pedidos propios |
| `/orders/[id]` | Seguimiento con polling cada 4 s |
| `/reservations` | Crear, listar y cancelar reservas propias |

### Backoffice

| Ruta | Permiso requerido | Descripción |
|---|---|---|
| `/staff` | Personal | Panel filtrado por permisos efectivos |
| `/staff/orders` | `orders:create` | Pedido de mostrador, cobro y filtros |
| `/staff/reservations` | `reservations:manage_all` | Confirmar/cancelar reservas |
| `/staff/history` | `history:read_all` | Historial paginado y línea de tiempo |
| `/staff/cancel` | `orders:read_all` | Cancelar pedidos aún no pagados |
| `/staff/products` | `products:manage` | Crear productos |
| `/staff/products/[id]` | `products:manage` | Editar o retirar producto |
| `/staff/users` | `users:manage_operational` | Crear/desactivar cuentas y asignar roles |
| `/staff/roles` | `roles:manage` | Matriz de permisos y auditoría |
| `/staff/reports` | `reports:read` | Ventas, tiempos y exportación CSV/PDF |
| `/staff/kitchen` | `kitchen:read` | Cola de cocina de respaldo (solo lectura) |

### Route handlers propios

| Ruta | Uso |
|---|---|
| `POST /api/auth/login` | Login contra Identity Service y cookies httpOnly |
| `POST /api/auth/register` | Registro público (siempre rol `cliente`) |
| `POST /api/auth/refresh` | Renovación de tokens y cookies |
| `POST /api/auth/logout` | Limpieza de cookies |
| `GET /api/orders/[id]/status` | Proxy autenticado para polling del pedido |
| `GET /api/queue` | Proxy autenticado de la cola de cocina |
| `POST /api/reports/export` | Proxy autenticado de exportación a RustFS |

## 2. Autenticación y sesión

1. El formulario de login envía las credenciales al route handler.
2. El handler llama a `POST /auth/login` del API Gateway y guarda
   `access_token`/`refresh_token` en cookies `httpOnly`, `SameSite=Lax` y `Secure`
   según `COOKIE_SECURE`.
3. `middleware.ts` valida la vigencia del access token y, si expiró, intenta
   refrescar contra el Gateway antes de continuar.
4. `lib/session.ts` decodifica el payload del JWT solo para leer claims de
   presentación (`sub`, `email`, `roles`, `permissions`, `level`). La firma la valida
   el Gateway; esta decodificación no sustituye la autorización del backend.
5. Cada Server Action y cada fetch a servicios usa `lib/api.ts`, que lee el access
   token de la cookie y añade `Authorization: Bearer …`.

### Reglas de acceso en middleware

- Rutas públicas: `/`, `/menu`, `/login`, `/register`, `/forbidden`, `/health`.
- `/checkout*`, `/orders*` y `/reservations*`: sesión autenticada con el permiso
  correspondiente (`orders:create_own`, `orders:read_own` o `read_all`, etc.).
- `/staff*`: además de sesión, exige el permiso del módulo. El panel `/staff`
  filtra los módulos visibles, pero el middleware y el backend vuelven a validar.
- `/api/*` y assets estáticos no pasan por las reglas de páginas.

## 3. Carrito y totales

- `lib/cart.ts` guarda `{ product_id: quantity }` en una cookie httpOnly.
- El carrito nunca envía precios ni totales al backend.
- `lib/actions.ts` construye el pedido solo con `product_id` y `quantity`.
- Order Service recalcula el total con el catálogo y rechaza montos manipulados.
- El pago usa `Idempotency-Key` para que reintentar no duplique el cobro.

## 4. Server Actions

| Acción | Efecto |
|---|---|
| `addToCart`, `updateCartLine`, `removeCartLine` | Mutan la cookie del carrito |
| `createOrder` | Crea pedido propio y redirige al pago |
| `createStaffOrder` | Crea pedido de mostrador con varios ítems |
| `payOrder` | Paga el total calculado por el servidor |
| `cancelOrder` | Cancela un pedido pendiente (supervisor/admin) |
| `createReservation`, `changeReservationStatus` | Reservas propias o del turno |
| `createProduct`, `updateProduct`, `deleteProduct` | Catálogo |
| `createUser`, `updateUser`, `assignRole`, `removeRole` | Cuentas y roles |

Los errores de API se redirigen a la misma pantalla con `?error=…`; los éxitos usan
`?ok=…`. Las páginas muestran ambos con `components/Alert.tsx`.

## 5. Estructura

```text
web-ssr/
├── app/
│   ├── (auth)/login, register
│   ├── (shop)/menu, cart, checkout, orders, reservations
│   ├── (staff)/staff/...          # backoffice completo
│   ├── api/auth, api/orders, api/queue, api/reports
│   ├── layout.tsx
│   └── page.tsx
├── components/                    # NavBar, formularios, tablas, polling, KDS
├── lib/
│   ├── actions.ts                 # Server Actions
│   ├── api.ts                     # Cliente del Gateway con cookie httpOnly
│   ├── auth.ts                    # requireSession/requireStaff/requirePermission
│   ├── cart.ts                    # Carrito en cookie
│   ├── cookies.ts                 # Nombres y opciones de cookies
│   ├── format.ts                  # Moneda, fechas y etiquetas
│   ├── permissions.ts             # can(), isStaff()
│   ├── session.ts                 # Claims, expiración y reglas de acceso
│   └── types.ts                   # DTOs compartidos
├── middleware.ts
├── tests/e2e/                     # Playwright
└── README.md
```

## 6. Variables de entorno

| Variable | Uso |
|---|---|
| `PORT` | Puerto del servidor Next.js (3000) |
| `API_GATEWAY_URL` | URL interna del Gateway (`http://api-gateway:8080` en compose) |
| `JWT_COOKIE_NAME` | Nombre de la cookie de access token |
| `JWT_REFRESH_COOKIE_NAME` | Nombre de la cookie de refresh token |
| `COOKIE_SECURE` | `true` en HTTPS; `false` en local |
| `NEXT_PUBLIC_APP_NAME` | Nombre visible |
| `NEXT_PUBLIC_POLL_INTERVAL_MS` | Intervalo de polling del cliente |

Ver `.env.example` para los valores locales.

## 7. Desarrollo y pruebas

```bash
cd web-ssr
npm ci
npm run dev          # http://localhost:3000
npm run typecheck
npm run build
npm run test:e2e     # requiere el stack P2 arriba
```

Las pruebas Playwright cubren login, registro, tienda, reservas y denegaciones de
rutas. Para ejecutarlas se necesita el Gateway y los servicios levantados; el detalle
del stack está en `../docs/equipo/demo.md`.

## 8. Seguridad

- Tokens solo en cookies httpOnly; nunca en `localStorage` ni en el HTML.
- CSRF mitigado con `SameSite=Lax` y Server Actions de Next.js.
- El middleware oculta rutas, pero la autorización real está en Gateway y servicios.
- No se confía en precios, totales, IDs de cliente ni roles enviados por el navegador.
- El cliente solo puede crear pedidos `para_llevar`; el personal crea pedidos de
  mostrador.
- El backoffice muestra solo módulos permitidos; el backend responde `403` si se
  intenta una operación sin permiso.

## 9. Alcance

Implementado: tienda, carrito, checkout, pago simulado, seguimiento, reservas,
backoffice de pedidos, reservas, historial, cancelaciones, catálogo, cuentas, roles,
auditoría, reportes, exportación y vista web de cocina.

El KDS/POS Flutter sigue siendo el cliente principal de cocina para cambiar estados
y confirmar entregas; `/staff/kitchen` es una vista de respaldo de solo lectura.
