# API Gateway — UNDesparche

El **API Gateway** es el punto de entrada único del sistema. Es una aplicación **FastAPI stateless** que:

1. Verifica los tokens de Firebase (solo aquí, una vez por petición).
2. Consulta o crea al usuario en el servicio de Users.
3. Guarda los datos del usuario en un **caché TTL en memoria** (60 segundos por defecto).
4. Reenvía (proxy) las peticiones a los tres microservicios: **Users**, **Events** e **Inventory**, inyectando los headers de identidad del usuario (`X-User-*`) para que ellos no tengan que autenticar con Firebase.

El front **no habla directamente con los servicios**, todo pasa por el gateway.

---

## Arquitectura

```
Frontend  -->  API Gateway (FastAPI, puerto 8000)
                    |
         +------------+------------+------------+
         |            |            |            |
      Users       Events      Inventory
     (8001)       (8002)      (8003)
```

---

## Cómo maneja la autenticación (Stateless)

El gateway es **stateless**: no guarda sesiones ni cookies. El front debe enviar el token de Firebase en **cada petición** que requiera autenticación:

```
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

### Flujo de una petición protegida

1. El gateway lee el header `Authorization: Bearer <token>`.
2. Verifica el token con **Firebase Admin SDK** (solo en el gateway).
3. Valida que el correo termine en `@unal.edu.co`.
4. Valida que el correo esté verificado (`email_verified == true`).
5. Consulta el **caché TTL** (memoria, 60 segundos):
   - Si el usuario está ahí y no expiró → usa esos datos.
   - Si no existe o expiró → consulta a Users Service: `GET /users/?firebase_uid=<uid>`.
6. Si el usuario no existe en Users, el gateway pide crearlo (`POST /users/` con `firebase_uid`, `email`, `name`; Users fija `role=COMMUNITY`, `status=ACTIVE`, `faculty=null`).
7. Guarda el usuario en el caché y devuelve el objeto `CurrentUser`.
8. Reenvía la petición al servicio destino inyectando los headers `X-User-*`.

**Cuentas bloqueadas**: si `is_active` es `false`, el gateway responde **403 Forbidden**.

**Endpoints públicos** (sin token): listar eventos publicados, listar implementos disponibles, obtener un evento o implemento por ID. Estos llegan al servicio sin headers de usuario y el servicio los trata como anónimos.

---

## Caché de usuarios

| Característica | Detalle |
|----------------|---------|
| **Estructura** | Diccionario en memoria: `{ firebase_uid: { "data": CurrentUser, "expires_at": timestamp } }` |
| **TTL** | 60 segundos por defecto (`USER_CACHE_TTL_SECONDS` en `.env`) |
| **Limpieza pasiva** | Al leer una entrada expirada, se elimina y se trata como inexistente |
| **Limpieza activa** | Tarea `asyncio` que recorre el caché cada 60 segundos y borra expirados |
| **Invalidación manual** | Al hacer `PATCH` o `DELETE` a un usuario, se vacía todo el caché (`clear_user_cache()`) |
| **Limitación** | Vive en la RAM de cada proceso. Con varios workers de uvicorn, cada uno tiene el suyo propio. No se comparte entre servidores |

---

## Endpoints del gateway (para el front)

Todas las rutas están bajo `http://localhost:8000`. El gateway reenvía al servicio correspondiente según el prefijo.

---

### A. Usuario actual / Autenticación

#### `GET /users/me/`
Devuelve los datos del usuario autenticado actual. Sirve como punto de login/registro implícito: el gateway verifica el token, consulta o crea al usuario en Users, y lo devuelve.

**Autenticación:** Requerida (`Authorization: Bearer <token>`)

**Respuesta (200):**
```json
{
  "firebase_uid": "abc123def456",
  "email": "juan.perez@unal.edu.co",
  "name": "Juan Pérez",
  "role": "COMMUNITY",
  "faculty": "ING",
  "status": "ACTIVE",
  "is_active": true
}
```

---

### B. Users Service (proxy)

#### `GET /users/`
Lista usuarios con filtros opcionales por query params.

**Autenticación:** Requerida

**Query params opcionales:**
- `firebase_uid` — filtrar por UID de Firebase
- `role` — `SYS_ADMIN`, `EVENT_ADMIN`, `IMPLEMENT_ADMIN`, `COMMUNITY`
- `faculty` — código de facultad (`ING`, `MED`, etc.)
- `status` — `ACTIVE`, `SANCTIONED`
- `search` — buscar por email o nombre

**Respuesta (200):**
```json
[
  {
    "id": 1,
    "email": "juan.perez@unal.edu.co",
    "name": "Juan Pérez",
    "faculty": "ING",
    "status": "ACTIVE",
    "is_active": true,
    "role": "COMMUNITY",
    "date_joined": "2026-11-01T10:00:00Z",
    "firebase_uid": "abc123def456"
  }
]
```

#### `GET /users/{user_id}/`
Obtiene un usuario por su ID interno de Users.

**Autenticación:** Requerida

#### `PATCH /users/{user_id}/`
Actualiza un usuario (rol, estado, facultad, etc.). Solo `SYS_ADMIN` puede modificar a otro `SYS_ADMIN`; `IMPLEMENT_ADMIN` solo puede cambiar `status`.

**Autenticación:** Requerida

**Body (JSON):**
```json
{
  "role": "EVENT_ADMIN",
  "faculty": "ING",
  "status": "ACTIVE"
}
```

**Respuesta (200):** el usuario actualizado.

#### `DELETE /users/{user_id}/`
Elimina un usuario. No se puede eliminar a otro `SYS_ADMIN`.

**Autenticación:** Requerida

---

### C. Events Service (proxy)

#### `GET /events/`
Lista eventos. **Público**: sin token se ven solo los publicados. Con token, el organizador puede ver también sus borradores.

**Autenticación:** Opcional

**Query params opcionales:**
- `category` — `ACA`, `CUL`, `DEP`, `ASA`, `PAR`, `OTR`
- `status` — `PRO`, `ECU`, `CAN`, `FIN`
- `published` — `true` / `false`
- `search` — buscar por nombre o lugar
- `ordering` — `datetime_start`, `name`, etc.

**Respuesta (200):**
```json
[
  {
    "id": 1,
    "name": "Charla de Ingeniería",
    "description": "Descripción del evento",
    "published": true,
    "place": "Auditorio principal",
    "latitude": 4.6543210,
    "longitude": -74.1234567,
    "datetime_start": "2026-11-15T14:00:00Z",
    "datetime_end": "2026-11-15T16:00:00Z",
    "organizer": { "id": "firebase_uid_123", "email": "organizador@unal.edu.co" },
    "status": "PRO",
    "category": "ACA",
    "image": "https://storage.googleapis.com/bucket/file.jpg",
    "is_subscribed": false
  }
]
```

#### `GET /events/{event_id}/`
Obtiene un evento por ID. **Público** (solo publicados si no hay token).

#### `POST /events/`
Crea un evento. **Requiere autenticación**. Soporta `multipart/form-data` para subir imagen.

**Autenticación:** Requerida

**Body (multipart/form-data):**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `name` | string | Nombre del evento |
| `description` | string | Descripción |
| `place` | string | Lugar |
| `latitude` | decimal | Latitud |
| `longitude` | decimal | Longitud |
| `datetime_start` | string (ISO 8601) | Fecha y hora de inicio |
| `datetime_end` | string (ISO 8601) | Fecha y hora de fin |
| `category` | string | `ACA`, `CUL`, `DEP`, `ASA`, `PAR`, `OTR` |
| `image_file` | archivo | Imagen opcional |

**Ejemplo JSON:**
```json
{
  "name": "Charla de Ingeniería",
  "description": "Charla sobre arquitectura de software",
  "place": "Auditorio principal",
  "latitude": 4.6543210,
  "longitude": -74.1234567,
  "datetime_start": "2026-11-15T14:00:00Z",
  "datetime_end": "2026-11-15T16:00:00Z",
  "category": "ACA"
}
```

**Respuesta (201):** el evento creado (misma estructura que el listado).

#### `PATCH /events/{event_id}/`
Actualiza un evento. Solo el dueño o `SYS_ADMIN`/`EVENT_ADMIN`. Soporta `multipart/form-data`.

**Autenticación:** Requerida

#### `DELETE /events/{event_id}/`
Elimina un evento. Solo `SYS_ADMIN` puede eliminar eventos publicados.

**Autenticación:** Requerida

#### `POST /events/{event_id}/publish/`
Publica un evento (cambia `published` a `true`).

**Autenticación:** Requerida

**Respuesta (200):** el evento publicado.

#### `POST /events/{event_id}/subscribe/`
Suscribe al usuario al evento. **Autenticación opcional**: un usuario externo puede suscribirse con solo su correo en el body (RF-7).

**Body (JSON):**
```json
{
  "email": "externo@correo.com"
}
```

Si el usuario está autenticado, se usa su `firebase_uid` y correo del token.

**Respuesta (201):**
```json
{
  "detail": "Suscripción realizada correctamente."
}
```

#### `POST /events/{event_id}/unsubscribe/`
Cancela la suscripción. **Autenticación opcional.**

**Body (JSON):**
```json
{
  "email": "externo@correo.com"
}
```

**Respuesta (200):**
```json
{
  "detail": "Suscripción cancelada correctamente."
}
```

---

### D. Inventory Service (proxy)

#### `GET /inventory/implements/`
Lista implementos. **Público**.

**Query params opcionales:**
- `category` — `BAL`, `RAQ`, `MES`, `JUR`, `JUM`, `OTS`
- `faculty` — código de facultad
- `state` — `DIS`, `NDS`, `RES`, `PRE`
- `search` — buscar por nombre o descripción

**Respuesta (200):**
```json
[
  {
    "id": 1,
    "name": "Balón de fútbol",
    "category": "BAL",
    "faculty": "ING",
    "state": "DIS",
    "description": "Balón oficial de talla 5",
    "image": null
  }
]
```

#### `GET /inventory/implements/{implement_id}/`
Obtiene un implemento por ID. **Público**.

#### `POST /inventory/implements/`
Crea un implemento. **Requiere autenticación**. Soporta `multipart/form-data`.

**Autenticación:** Requerida

**Body (multipart/form-data):**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `name` | string | Nombre del implemento |
| `category` | string | `BAL`, `RAQ`, `MES`, `JUR`, `JUM`, `OTS` |
| `faculty` | string | Facultad propietaria (requerido si es SYS_ADMIN) |
| `state` | string | `DIS`, `NDS`, `RES`, `PRE` |
| `description` | string | Descripción |
| `image_file` | archivo | Imagen opcional |

**Ejemplo JSON:**
```json
{
  "name": "Balón de fútbol",
  "category": "BAL",
  "faculty": "ING",
  "state": "DIS",
  "description": "Balón oficial de talla 5"
}
```

**Respuesta (201):** el implemento creado.

#### `PATCH /inventory/implements/{implement_id}/`
Actualiza un implemento. `IMPLEMENT_ADMIN` solo puede modificar los de su facultad.

**Autenticación:** Requerida

#### `DELETE /inventory/implements/{implement_id}/`
Elimina un implemento.

**Autenticación:** Requerida

#### `GET /inventory/reserves/`
Lista reservas del usuario autenticado. **Requiere autenticación**.

**Respuesta (200):**
```json
[
  {
    "id": 1,
    "implement": 5,
    "implement_name": "Balón de fútbol",
    "implement_state": "RES",
    "user_id": "firebase_uid_123",
    "datetime_reserved": "2026-11-01T10:00:00Z",
    "datetime_expiration": "2026-11-01T10:10:00Z",
    "active": true
  }
]
```

#### `POST /inventory/reserves/`
Crea una reserva (10 minutos de expiración). **Requiere autenticación**.

**Body (JSON):**
```json
{
  "implement": 5
}
```

**Respuesta (201):** la reserva creada.

#### `POST /inventory/reserves/{reserve_id}/confirm/`
Confirma una reserva (pasa a préstamo). **Requiere autenticación**.

**Respuesta (201):** el préstamo creado.

#### `POST /inventory/reserves/{reserve_id}/cancel/`
Cancela una reserva. **Requiere autenticación**.

**Respuesta (200):**
```json
{
  "detail": "Reserva cancelada correctamente."
}
```

#### `GET /inventory/borrowings/`
Lista préstamos del usuario. **Requiere autenticación**.

#### `GET /inventory/borrowings/{borrowing_id}/`
Obtiene un préstamo por ID. **Requiere autenticación**.

#### `POST /inventory/borrowings/{borrowing_id}/return/`
Devuelve un implemento prestado. **Requiere autenticación**.

**Respuesta (200):**
```json
{
  "detail": "Devolución registrada correctamente."
}
```

---

## Cabeceras que el gateway inyecta a los servicios (NO las envía el front)

El front **no debe enviar** estos headers. El gateway los añade automáticamente al reforwarder la petición a los microservicios:

| Cabecera | Valor ejemplo | Descripción |
|----------|--------------|-------------|
| `X-From-Gateway` | `true` | Marca que la petición proviene del gateway |
| `X-User-Id` | `firebase_uid_123` | UID de Firebase del usuario |
| `X-User-Email` | `juan.perez@unal.edu.co` | Correo institucional |
| `X-User-Role` | `EVENT_ADMIN` | Rol del usuario |
| `X-User-Faculty` | `ING` | Facultad (vacío si no tiene) |
| `X-User-Status` | `ACTIVE` | Estado (`ACTIVE`, `SANCTIONED`) |

**Nota importante:** `X-User-Name` **no se envía** por headers porque los nombres con tildes o eñes pueden romperse en HTTP (solo ASCII). El nombre viaja en el body cuando es necesario (crear/actualizar evento o implemento).

---

## Variables de entorno (`.env` del gateway)

```
FIREBASE_CREDENTIALS_PATH=firebase_credentials.json
USERS_SERVICE_URL=http://localhost:8001
EVENTS_SERVICE_URL=http://localhost:8002
INVENTORY_SERVICE_URL=http://localhost:8003
USER_CACHE_TTL_SECONDS=60
HTTP_TIMEOUT_SECONDS=30
CORS_ORIGINS=http://localhost:3000
```

---

## Cómo levantar el gateway

```powershell
cd gateway
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

Requisitos previos:
- `firebase-credentials.json` en la carpeta del gateway (o ruta definida en `FIREBASE_CREDENTIALS_PATH`).
- Los tres servicios levantados en sus respectivos puertos (8001, 8002, 8003).

---

# Resumen rápido para el front

| Quiero hacer | Endpoint del gateway | Método | ¿Token? | ¿Body? |
|--------------|---------------------|--------|---------|--------|
| Ver mi perfil | `/users/me/` | GET | Sí | No |
| Listar usuarios | `/users/` | GET | Sí | No |
| Ver un usuario | `/users/{id}/` | GET | Sí | No |
| Editar un usuario | `/users/{id}/` | PATCH | Sí | JSON |
| Borrar un usuario | `/users/{id}/` | DELETE | Sí | No |
| Listar eventos | `/events/` | GET | Opcional | No |
| Ver un evento | `/events/{id}/` | GET | Opcional | No |
| Crear evento | `/events/` | POST | Sí | JSON o multipart |
| Editar evento | `/events/{id}/` | PATCH | Sí | JSON o multipart |
| Borrar evento | `/events/{id}/` | DELETE | Sí | No |
| Publicar evento | `/events/{id}/publish/` | POST | Sí | No |
| Suscribirse a evento | `/events/{id}/subscribe/` | POST | Opcional | JSON (`{"email": "..."}`) |
| Desuscribirse | `/events/{id}/unsubscribe/` | POST | Opcional | JSON (`{"email": "..."}`) |
| Listar implementos | `/inventory/implements/` | GET | Opcional | No |
| Ver un implemento | `/inventory/implements/{id}/` | GET | Opcional | No |
| Crear implemento | `/inventory/implements/` | POST | Sí | JSON o multipart |
| Editar implemento | `/inventory/implements/{id}/` | PATCH | Sí | JSON o multipart |
| Borrar implemento | `/inventory/implements/{id}/` | DELETE | Sí | No |
| Listar reservas | `/inventory/reserves/` | GET | Sí | No |
| Crear reserva | `/inventory/reserves/` | POST | Sí | JSON (`{"implement": 5}`) |
| Confirmar reserva | `/inventory/reserves/{id}/confirm/` | POST | Sí | No |
| Cancelar reserva | `/inventory/reserves/{id}/cancel/` | POST | Sí | No |
| Listar préstamos | `/inventory/borrowings/` | GET | Sí | No |
| Ver un préstamo | `/inventory/borrowings/{id}/` | GET | Sí | No |
| Devolver implemento | `/inventory/borrowings/{id}/return/` | POST | Sí | No |
