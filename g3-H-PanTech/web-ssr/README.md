# Web SSR — Panadería Express

Aplicación web renderizada en el servidor con **Next.js 14 (App Router)** y
**TypeScript**. Es el único componente que el navegador consume directamente:
todas las llamadas al backend pasan por el **API Gateway**. Los tokens viven en
cookies `httpOnly`; nunca se guardan en `localStorage` ni se imprimen en el HTML.

| Campo | Valor |
|---|---|
| Framework | Next.js 14.2 · React 18.3 |
| Lenguaje | TypeScript estricto |
| Puerto | `3000` |
| Backend | `API_GATEWAY_URL` (`http://api-gateway:8080` en Docker) |
| Sesión | cookies `panaderia_access` y `panaderia_refresh` |

---

## 1. Rutas

### Implementadas en este PR

| Ruta | Acceso | Permiso | Descripción |
|---|---|---|---|
| `/` | Público | — | Portada SSR con el resumen del servicio. |
| `/login` | Público | — | Formulario de login; redirige según el rol. |
| `/register` | Público | — | Registro público; siempre crea `cliente`. |
| `/staff` | Personal | rol de personal | Panel del backoffice con permisos efectivos. |
| `/forbidden` | Público | — | Página 403. |
| `/health` | Público | — | Liveness de la Web SSR. |

### Preparadas para las siguientes fases

El middleware ya conoce estas rutas y sus permisos, aunque las páginas se
implementan en las fases 2 y 3 del plan:

| Ruta | Quién | Permiso |
|---|---|---|
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
| `/staff/kitchen` | Personal autorizado | `kitchen:read` |

---

## 2. Flujo de autenticación

```text
Navegador ──POST /api/auth/login──▶ Web SSR (route handler)
                                      │
                                      ├─POST /auth/login──▶ API Gateway ──▶ Identity
                                      │
                                      ◀── access + refresh ────────────────
                                      │
                                      ├─ Set-Cookie httpOnly (access, refresh)
                                      ◀── { user } ────────────────────────
Navegador ──GET /staff──▶ middleware ── valida cookie / refresca ──▶ página SSR
```

- El **route handler** del login/registro/refresh guarda los tokens en cookies
  `httpOnly`, `sameSite=lax` y `secure` cuando `COOKIE_SECURE=true`.
- El **middleware** decodifica el JWT solo para decidir navegación y permisos;
  si el access token expiró, intenta `/auth/refresh` contra el Gateway y rota
  las cookies. Si no hay sesión válida, redirige a `/login?next=...`.
- Las **páginas de servidor** vuelven a leer la sesión (`requireSession`,
  `requireStaff`, `requirePermission`) y `lib/api.ts` adjunta el Bearer al
  llamar al Gateway.
- El backend siempre revalida permisos: ocultar un botón en la UI no es una
  medida de seguridad.

### Cookies

| Cookie | Contenido | Flags |
|---|---|---|
| `panaderia_access` | JWT de acceso (15 min) | `httpOnly`, `sameSite=lax`, `path=/`, `secure` en producción |
| `panaderia_refresh` | Refresh token opaco (7 días) | iguales |

Nombres configurables con `JWT_COOKIE_NAME` y `JWT_REFRESH_COOKIE_NAME`.

### Route handlers propios

| Ruta | Función |
|---|---|
| `POST /api/auth/login` | Proxy al Gateway, setea cookies. |
| `POST /api/auth/register` | Proxy al registro público, setea cookies. |
| `POST /api/auth/refresh` | Rota tokens usando la cookie de refresh. |
| `POST /api/auth/logout` | Revoca en el Gateway y limpia cookies siempre. |
| `GET /health` | `{"status":"ok"}`. |

Ningún route handler devuelve el access o refresh token al navegador.

---

## 3. Estructura

```text
web-ssr/
├── app/
│   ├── (auth)/login/page.tsx       # login SSR
│   ├── (auth)/register/page.tsx    # registro SSR
│   ├── (staff)/staff/page.tsx      # backoffice protegido
│   ├── api/auth/*/route.ts         # cookies + proxy al Gateway
│   ├── forbidden/page.tsx          # 403
│   ├── health/route.ts             # liveness
│   ├── globals.css
│   ├── layout.tsx
│   └── page.tsx
├── components/
│   ├── LoginForm.tsx
│   ├── RegisterForm.tsx
│   ├── NavBar.tsx
│   └── LogoutButton.tsx
├── lib/
│   ├── api.ts          # cliente del Gateway con Bearer desde la cookie
│   ├── auth.ts         # requireSession / requireStaff / requirePermission
│   ├── cookies.ts      # nombres y flags de cookies
│   ├── permissions.ts  # can() / canAny() / isStaff()
│   ├── session.ts      # decodificación del JWT y reglas de rutas
│   └── types.ts
├── middleware.ts       # protección de rutas y refresh transparente
├── tests/e2e/          # Playwright
└── playwright.config.ts
```

---

## 4. Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `API_GATEWAY_URL` | `http://localhost:8080` | URL interna del API Gateway |
| `JWT_COOKIE_NAME` | `panaderia_access` | Nombre de la cookie de acceso |
| `JWT_REFRESH_COOKIE_NAME` | `panaderia_refresh` | Nombre de la cookie de refresh |
| `COOKIE_SECURE` | `false` | `true` solo con HTTPS |
| `NEXT_PUBLIC_APP_NAME` | `Panadería Express` | Nombre visible |
| `NEXT_PUBLIC_POLL_INTERVAL_MS` | `4000` | Polling de seguimiento (fases siguientes) |
| `E2E_BASE_URL` | `http://localhost:3000` | Base de Playwright |
| `E2E_CLIENT_EMAIL` / `E2E_CLIENT_PASSWORD` | `cliente@demo.com` / `Demo1234!` | Credenciales E2E |
| `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD` | `admin@demo.com` / `Demo1234!` | Credenciales E2E |

Copia `.env.example` como `.env.local` para desarrollo.

---

## 5. Desarrollo y pruebas

```bash
npm install
npm run dev          # http://localhost:3000
npm run typecheck    # TypeScript estricto
npm run build        # build de producción
npm run start        # sirve el build
```

Las pruebas E2E requieren el stack levantado (Gateway + Identity + Web SSR) y
los usuarios demo sembrados:

```bash
npx playwright install chromium   # una vez
npm run test:e2e
```

Qué cubren:

- Login de cliente y de admin, redirección por rol.
- Cookies `httpOnly`, `sameSite=lax`, sin tokens en `localStorage`.
- Credenciales inválidas y registro público como `cliente`.
- Logout y limpieza de cookies.
- SSR real: el HTML inicial contiene la portada.
- `/staff` sin sesión redirige a login; cliente autenticado recibe **403**.
- `/health` responde `{"status":"ok"}`.

---

## 6. Reglas de seguridad

- Solo se llama al **API Gateway**; nunca a Identity, Order, Kitchen o Reporting
  directamente desde el navegador.
- Los tokens no se exponen en el HTML ni en `localStorage`.
- El middleware y las páginas de servidor comprueban sesión y permisos.
- El logout limpia las cookies aunque el Gateway no responda.
- `secure` se activa en producción con `COOKIE_SECURE=true`.
- La UI oculta acciones sin permiso, pero el backend es la autoridad final.

---

## 7. Alcance del primer PR

Incluye el shell SSR, login, registro, middleware, cliente del Gateway, `/staff`
informativo y pruebas E2E. **No** incluye todavía la tienda del cliente ni los
módulos completos del backoffice (pedidos, productos, usuarios, roles, reportes
y KDS), que se implementan en las fases 2 y 3 del plan de trabajo.
