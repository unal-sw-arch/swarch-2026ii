# Servicio de Inventory — UNDesparche

Este servicio gestiona los **implementos recreativos**, sus **reservas** y sus **préstamos**.

No tiene tabla de usuarios propia. Identifica al usuario a través del `firebase_uid` que llega en los headers `X-User-Id` inyectados por el API Gateway. Las reservas y préstamos guardan `user_id` como `CharField` (el `firebase_uid`), no como llave foránea a una tabla local de usuarios.

---

## Responsabilidades

- Registrar, consultar, actualizar y eliminar implementos recreativos.
- Gestionar disponibilidad de implementos (`DIS`, `NDS`, `RES`, `PRE`).
- Crear, confirmar y cancelar reservas (10 minutos de expiración).
- Gestionar préstamos y devoluciones de implementos.
- Subir y eliminar imágenes de implementos en Firebase Storage.
- Liberar automáticamente reservas expiradas.

---

## Endpoints del servicio (vía API Gateway)

Este servicio **no se expone directamente al front**. Todas las peticiones pasan por el API Gateway en el puerto 8000.

El servicio escucha en el puerto 8003, pero su CORS solo acepta peticiones originadas desde el Gateway (`http://localhost:8000`).

### Implementos

| Método | Ruta (vía Gateway) | Ruta directa (solo Gateway) | Descripción | Autenticación |
|--------|-------------------|----------------------------|-------------|---------------|
| `GET` | `/inventory/implements/` | `http://localhost:8003/implements/` | Listar implementos (filtros: category, faculty, state, search) | Público |
| `GET` | `/inventory/implements/{pk}/` | `http://localhost:8003/implements/{pk}/` | Obtener un implemento por ID | Público |
| `POST` | `/inventory/implements/` | `http://localhost:8003/implements/` | Crear implemento (multipart: imagen opcional) | `X-From-Gateway` + `X-User-*` |
| `PATCH` | `/inventory/implements/{pk}/` | `http://localhost:8003/implements/{pk}/` | Actualizar implemento | `X-From-Gateway` + `X-User-*` + facultad |
| `DELETE` | `/inventory/implements/{pk}/` | `http://localhost:8003/implements/{pk}/` | Eliminar implemento | `X-From-Gateway` + `X-User-*` |

### Reservas

| Método | Ruta (vía Gateway) | Ruta directa (solo Gateway) | Descripción | Autenticación |
|--------|-------------------|----------------------------|-------------|---------------|
| `GET` | `/inventory/reserves/` | `http://localhost:8003/reserves/` | Listar reservas del usuario | `X-From-Gateway` + `X-User-*` |
| `POST` | `/inventory/reserves/` | `http://localhost:8003/reserves/` | Crear reserva (10 min expiración) | `X-From-Gateway` + `X-User-*` |
| `POST` | `/inventory/reserves/{pk}/confirm/` | `http://localhost:8003/reserves/{pk}/confirm/` | Confirmar reserva (pasa a préstamo) | `X-From-Gateway` + `X-User-*` |
| `POST` | `/inventory/reserves/{pk}/cancel/` | `http://localhost:8003/reserves/{pk}/cancel/` | Cancelar reserva | `X-From-Gateway` + `X-User-*` |

### Préstamos

| Método | Ruta (vía Gateway) | Ruta directa (solo Gateway) | Descripción | Autenticación |
|--------|-------------------|----------------------------|-------------|---------------|
| `GET` | `/inventory/borrowings/` | `http://localhost:8003/borrowings/` | Listar préstamos del usuario | `X-From-Gateway` + `X-User-*` |
| `GET` | `/inventory/borrowings/{pk}/` | `http://localhost:8003/borrowings/{pk}/` | Obtener un préstamo por ID | `X-From-Gateway` + `X-User-*` |
| `POST` | `/inventory/borrowings/{pk}/return/` | `http://localhost:8003/borrowings/{pk}/return/` | Devolver implemento prestado | `X-From-Gateway` + `X-User-*` |

> El servicio también expone las mismas rutas bajo el prefijo `/inventory/` (por configuración de enrutamiento), pero el API Gateway llama directamente a `/implements/`, `/reserves/` y `/borrowings/`.

**Modelo de CORS:** Este servicio solo acepta peticiones con origen `http://localhost:8000` (el API Gateway). Cualquier petición directa desde el front o cualquier otro origen será rechazada. El front **nunca** debe llamar directamente a este servicio; todo pasa por el Gateway en el puerto 8000.

---

## Cabeceras que espera el servicio

| Cabecera | Valor ejemplo | Descripción |
|----------|--------------|-------------|
| `X-From-Gateway` | `true` | Marca que viene del gateway |
| `X-User-Id` | `firebase_uid_123` | UID de Firebase (se guarda como `user_id` en reservas/préstamos) |
| `X-User-Email` | `usuario@unal.edu.co` | Correo del usuario |
| `X-User-Role` | `IMPLEMENT_ADMIN` | Rol para permisos |
| `X-User-Faculty` | `ING` | Facultad (crítico para IMPLEMENT_ADMIN) |
| `X-User-Status` | `ACTIVE` | Estado (ACTIVE/SANCTIONED) |

Si no se envía `X-From-Gateway` ni `X-User-Id`, la petición se trata como **anónima**.

---

## Cuerpo de creación de implemento (`POST /implements/`)

Soporta `application/json` o `multipart/form-data` (para subir imagen).

**Campos:**
| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `name` | string | Sí | Nombre del implemento |
| `category` | string | No | `BAL`, `RAQ`, `MES`, `JUR`, `JUM`, `OTS` |
| `faculty` | string | No* | Facultad propietaria (requerido si es SYS_ADMIN) |
| `state` | string | No | `DIS`, `NDS`, `RES`, `PRE` |
| `description` | string | No | Descripción |
| `image_file` | archivo | No | Imagen del implemento |

*\*SYS_ADMIN debe especificar faculty. Otros roles hereden la facultad del usuario.*

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

**Respuesta (201):**
```json
{
  "id": 1,
  "name": "Balón de fútbol",
  "category": "BAL",
  "faculty": "ING",
  "state": "DIS",
  "description": "Balón oficial de talla 5",
  "image": null
}
```

---

## Cuerpo de creación de reserva (`POST /reserves/`)

**Body (JSON):**
```json
{
  "implement": 5
}
```

**Respuesta (201):**
```json
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
```

---

## Estados de implementos

| Estado | Código | Descripción |
|--------|--------|-------------|
| Disponible | `DIS` | Puede reservarse |
| No Disponible | `NDS` | No disponible |
| Reservado | `RES` | Reservado (10 min) |
| Prestado | `PRE` | Prestado físicamente |

| Categoría | Código | Descripción |
|-----------|--------|-------------|
| Balones | `BAL` | Balones deportivos |
| Raquetas | `RAQ` | Raquetas |
| Mesas | `MES` | Mesas |
| Juegos recreativos | `JUR` | Juegos recreativos |
| Juegos de mesa | `JUM` | Juegos de mesa |
| Otros | `OTS` | Otros |

---

## Facultades

| Código | Facultad |
|--------|----------|
| `ART` | Artes |
| `CCS` | Ciencias |
| `CIA` | Ciencias Agrarias |
| `CIE` | Ciencias Económicas |
| `CHS` | Ciencias Humanas |
| `DER` | Derecho, Ciencias Políticas y Sociales |
| `ENF` | Enfermería |
| `ING` | Ingeniería |
| `MED` | Medicina |
| `MVZ` | Medicina Veterinaria y Zootecnia |
| `ODO` | Odontología |
