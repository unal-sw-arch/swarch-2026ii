# Servicio de Usuarios — UNDesparche

Este servicio gestiona los **usuarios del sistema**: sus datos personales, rol, facultad, estado y vínculo con Firebase Authentication (`firebase_uid`).

Es el **único servicio que tiene tabla propia de usuarios**. Events e Inventory no almacenan usuarios; solo guardan el `firebase_uid` (`user_id`) para saber quién hace qué.

---

## Responsabilidades

- Crear usuarios al autenticarse por primera vez con Firebase (el gateway lo solicita).
- Consultar usuarios por `firebase_uid`, correo, rol, facultad o estado.
- Actualizar rol, estado y facultad de un usuario.
- Eliminar usuarios (excepto a otros `SYS_ADMIN`).
- Proveer el endpoint `GET /users/me/` (usado por el gateway como punto de login/registro).

---

## Endpoints del servicio (vía API Gateway)

Este servicio **no se expone directamente al front**. Todas las peticiones pasan por el API Gateway en el puerto 8000.

El servicio escucha en el puerto 8001, pero su CORS solo acepta peticiones originadas desde el Gateway (`http://localhost:8000`).

| Método | Ruta (vía Gateway) | Ruta directa (solo Gateway) | Descripción | Autenticación |
|--------|-------------------|----------------------------|-------------|---------------|
| `GET` | `/users/` | `http://localhost:8001/users/` | Listar usuarios con filtros (`firebase_uid`, `role`, `faculty`, `status`, `search`) | `X-From-Gateway: true` |
| `GET` | `/users/{pk}/` | `http://localhost:8001/users/{pk}/` | Obtener un usuario por ID | `X-From-Gateway: true` |
| `POST` | `/users/` | `http://localhost:8001/users/` | Crear usuario (solo gateway) | `X-From-Gateway: true` |
| `PATCH` | `/users/{pk}/` | `http://localhost:8001/users/{pk}/` | Actualizar usuario (rol, estado, facultad) | `X-From-Gateway: true` |
| `DELETE` | `/users/{pk}/` | `http://localhost:8001/users/{pk}/` | Eliminar usuario (no se puede eliminar otro SYS_ADMIN) | `X-From-Gateway: true` |

> **Nota:** Este servicio ya **no autentica con Firebase**. Confía en los headers que inyecta el API Gateway (`X-From-Gateway: true` y `X-User-Id`). El front nunca debe llamar directamente a este servicio; todo pasa por el Gateway en el puerto 8000.

---

## Cabeceras que espera el servicio

| Cabecera | Valor ejemplo | Cuándo se envía |
|----------|--------------|-----------------|
| `X-From-Gateway` | `true` | Siempre en llamadas desde el gateway |
| `X-User-Id` | `firebase_uid_123` | Cuando el gateway identifica a un usuario |
| `X-User-Email` | `usuario@unal.edu.co` | Datos del usuario (informativos) |
| `X-User-Role` | `COMMUNITY` | Rol del usuario |
| `X-User-Faculty` | `ING` | Facultad del usuario |
| `X-User-Status` | `ACTIVE` | Estado del usuario |

**Importante:** el servicio **no acepta** `Authorization: Bearer <firebase_token>` directamente. Esa verificación la hace exclusivamente el gateway.

**Modelo de CORS:** Este servicio solo acepta peticiones con origen `http://localhost:8000` (el API Gateway). Cualquier petición directa desde el front o cualquier otro origen será rechazada. El front **nunca** debe llamar directamente a este servicio; todo pasa por el Gateway en el puerto 8000.

---

## Cuerpo de creación (`POST /users/`)

El gateway envía solo la identidad. El servidor fija `role=COMMUNITY`, `status=ACTIVE`, `faculty=null`.

```json
{
  "firebase_uid": "abc123def456",
  "email": "juan.perez@unal.edu.co",
  "name": "Juan Pérez"
}
```

**Respuesta (201):**
```json
{
  "id": 1,
  "email": "juan.perez@unal.edu.co",
  "name": "Juan Pérez",
  "faculty": null,
  "status": "ACTIVE",
  "is_active": true,
  "role": "COMMUNITY",
  "date_joined": "2026-11-01T10:00:00Z",
  "firebase_uid": "abc123def456"
}
```

---

## Roles y estados

| Rol | Descripción |
|-----|-------------|
| `SYS_ADMIN` | Administrador del Sistema |
| `EVENT_ADMIN` | Administrador de Eventos |
| `IMPLEMENT_ADMIN` | Administrador de Implementos |
| `COMMUNITY` | Miembro de la Comunidad |

| Estado | Descripción |
|--------|-------------|
| `ACTIVE` | Usuario activo |
| `SANCTIONED` | Usuario sancionado (no puede reservar) |
