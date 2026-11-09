# Servicio de Eventos — UNDesparche

Este servicio gestiona los **eventos recreativos** del campus: creación, publicación, consulta, suscripciones y desuscripciones.

No tiene tabla de usuarios propia. Identifica al usuario a través del `firebase_uid` que llega en los headers `X-User-Id` inyectados por el API Gateway.

---

## Responsabilidades

- Crear, consultar, actualizar y eliminar eventos.
- Publicar eventos (pasarlos de borrador a visible).
- Listar eventos publicados (público) y borradores del organizador (autenticado).
- Suscribirse y desuscribirse a eventos (usuarios registrados o externos con correo).
- Subir y eliminar imágenes de eventos en Firebase Storage.

---

## Endpoints del servicio (vía API Gateway)

Este servicio **no se expone directamente al front**. Todas las peticiones pasan por el API Gateway en el puerto 8000.

El servicio escucha en el puerto 8002, pero su CORS solo acepta peticiones originadas desde el Gateway (`http://localhost:8000`).

| Método | Ruta (vía Gateway) | Ruta directa (solo Gateway) | Descripción | Autenticación |
|--------|-------------------|----------------------------|-------------|---------------|
| `GET` | `/events/` | `http://localhost:8002/events/` | Listar eventos (filtros: category, status, published, search, ordering) | Público (AllowAny) |
| `GET` | `/events/{pk}/` | `http://localhost:8002/events/{pk}/` | Obtener un evento por ID | Público (AllowAny) |
| `POST` | `/events/` | `http://localhost:8002/events/` | Crear evento (multipart: imagen opcional) | `X-From-Gateway` + `X-User-*` |
| `PATCH` | `/events/{pk}/` | `http://localhost:8002/events/{pk}/` | Actualizar evento (multipart: imagen opcional) | `X-From-Gateway` + `X-User-*` + dueño/admin |
| `DELETE` | `/events/{pk}/` | `http://localhost:8002/events/{pk}/` | Eliminar evento | `X-From-Gateway` + `X-User-*` + SYS_ADMIN o dueño |
| `POST` | `/events/{pk}/publish/` | `http://localhost:8002/events/{pk}/publish/` | Publicar evento | `X-From-Gateway` + `X-User-*` |
| `POST` | `/events/{pk}/subscribe/` | `http://localhost:8002/events/{pk}/subscribe/` | Suscribirse a un evento | Público o autenticado |
| `POST` | `/events/{pk}/unsubscribe/` | `http://localhost:8002/events/{pk}/unsubscribe/` | Desuscribirse de un evento | Público o autenticado |

---

## Cabeceras que espera el servicio

| Cabecera | Valor ejemplo | Descripción |
|----------|--------------|-------------|
| `X-From-Gateway` | `true` | Marca que viene del gateway |
| `X-User-Id` | `firebase_uid_123` | UID de Firebase (se usa como `organizer_id`) |
| `X-User-Email` | `usuario@unal.edu.co` | Correo del usuario |
| `X-User-Role` | `EVENT_ADMIN` | Rol para permisos |
| `X-User-Faculty` | `ING` | Facultad (opcional) |
| `X-User-Status` | `ACTIVE` | Estado (opcional) |

Si no se envía `X-From-Gateway` ni `X-User-Id`, la petición se trata como **anónima**.

**Modelo de CORS:** Este servicio solo acepta peticiones con origen `http://localhost:8000` (el API Gateway). Cualquier petición directa desde el front o cualquier otro origen será rechazada. El front **nunca** debe llamar directamente a este servicio; todo pasa por el Gateway en el puerto 8000.

---

## Cuerpo de creación (`POST /events/`)

Soporta `application/json` o `multipart/form-data` (para subir imagen).

**Campos JSON/multipart:**
| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `name` | string | Sí | Nombre del evento |
| `description` | string | Sí | Descripción |
| `place` | string | Sí | Lugar |
| `latitude` | decimal | Sí | Latitud |
| `longitude` | decimal | Sí | Longitud |
| `datetime_start` | string ISO 8601 | Sí | Fecha/hora de inicio |
| `datetime_end` | string ISO 8601 | Sí | Fecha/hora de fin |
| `category` | string | No | `ACA`, `CUL`, `DEP`, `ASA`, `PAR`, `OTR` |
| `image_file` | archivo | No | Imagen del evento |

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

**Respuesta (201):**
```json
{
  "id": 1,
  "name": "Charla de Ingeniería",
  "description": "...",
  "published": false,
  "place": "Auditorio principal",
  "latitude": 4.6543210,
  "longitude": -74.1234567,
  "datetime_start": "2026-11-15T14:00:00Z",
  "datetime_end": "2026-11-15T16:00:00Z",
  "organizer": { "id": "firebase_uid_123", "email": "usuario@unal.edu.co" },
  "status": "PRO",
  "category": "ACA",
  "image": null,
  "is_subscribed": false
}
```

---

## Suscripción (`POST /events/{pk}/subscribe/`)

**Autenticación opcional (RF-7):** un usuario externo puede suscribirse con solo su correo.

**Body (JSON):**
```json
{
  "email": "externo@correo.com"
}
```

Si el usuario está autenticado, se usa su `firebase_uid` y correo del token.

---

## Estados y categorías

| Estado | Código | Descripción |
|--------|--------|-------------|
| Programado | `PRO` | Evento por realizarse |
| En Curso | `ECU` | Evento en ejecución |
| Finalizado | `FIN` | Evento terminado |
| Cancelado | `CAN` | Evento cancelado |

| Categoría | Código | Descripción |
|-----------|--------|-------------|
| Académico | `ACA` | Actividad académica |
| Cultural | `CUL` | Actividad cultural |
| Deportivo | `DEP` | Actividad deportiva |
| Asamblea | `ASA` | Asamblea |
| Parche | `PAR` | Recreativo |
| Otro | `OTR` | Otro |
