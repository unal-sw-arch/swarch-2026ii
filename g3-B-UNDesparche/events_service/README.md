# Documentación de la API — Events Service

## 1. Descripción general

**Events Service** administra eventos y suscripciones a eventos. Está implementado con Django REST Framework y expone un `EventViewSet` registrado en un `DefaultRouter`.

El servicio puede operar de forma independiente del **Users Service**: no consulta directamente su base de datos ni necesita una conexión HTTP entre ambos. Los dos servicios reconocen al mismo usuario mediante Firebase Authentication y comparten un contrato de claims en los tokens.

> **Base URL:** reemplaza `<BASE_URL>` por la URL raíz donde esté desplegado el servicio. Las rutas descritas son relativas a esa base y pueden estar precedidas por un prefijo configurado en el proyecto Django principal.

## 2. Autenticación y contrato de identidad con Users Service

### 2.1. Envío del token

Para las operaciones que requieren identidad, envía un Firebase ID token en el encabezado HTTP:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

`FirebaseAuthentication` verifica el token con Firebase Admin SDK. Si el encabezado no contiene el prefijo `Bearer `, la clase no autentica la solicitud y esta se trata como anónima cuando el endpoint permite acceso público. Si Firebase rechaza el token, la autenticación genera `401 Unauthorized`.

### 2.2. Claims compartidos

El contrato relevante entre ambos servicios es el siguiente:

| Claim | Procedencia / uso en Events Service |
|---|---|
| `uid` | Identificador Firebase del usuario. Se usa como `request.user.uid` y se guarda como `organizer_id` o `Subscription.user_id`. |
| `role` | Claim personalizado que determina los permisos. |
| `email` | Correo del usuario; se usa como correo de notificación para suscripciones autenticadas. |
| `name` | Nombre del usuario; se guarda como `organizer_name` al crear un evento. |

El Users Service publica el claim personalizado `role` en Firebase con el valor del rol almacenado para el usuario. El Events Service lee ese mismo claim del ID token. Los roles que aparecen en los archivos del sistema son:

| Valor del claim `role` | Significado |
|---|---|
| `SYS_ADMIN` | Administrador del Sistema |
| `EVENT_ADMIN` | Administrador de Eventos |
| `IMPLEMENT_ADMIN` | Administrador de Implementos |
| `COMMUNITY` | Miembro de la Comunidad |

**Contrato importante:** el token debe incluir un `uid` válido y un `role` vigente para que las operaciones autenticadas y la autorización funcionen correctamente. Events Service no consulta la base de datos del Users Service para completar o actualizar esos valores: trabaja con los claims verificados del token.

Si el Users Service cambia el rol de un usuario y publica nuevos claims, el frontend debe renovar el Firebase ID token para que el siguiente servicio reciba el `role` actualizado. El endpoint `POST /login/` del Users Service devuelve `claims_changed`; cuando es `true`, debe renovarse el token.

### 2.3. Reglas de autorización

| Operación | Regla principal |
|---|---|
| Listar y consultar eventos | Público. Los usuarios anónimos solo ven eventos publicados; un usuario autenticado ve eventos publicados y los que él organiza. |
| Crear eventos | Usuario con `role` no vacío y rol `SYS_ADMIN` o `EVENT_ADMIN`. |
| Modificar, publicar o eliminar | Usuario con `role` no vacío, rol `SYS_ADMIN` o `EVENT_ADMIN`, y permiso de propietario sobre el evento. `SYS_ADMIN` puede superar la comprobación de propietario. |
| Eliminar un evento publicado | Solo `SYS_ADMIN`, incluso si el usuario es propietario. |
| Suscribirse o cancelar suscripción | Público; puede identificarse con token o con correo electrónico. |

`IsRegisteredUser` comprueba que el claim `role` sea verdadero/no vacío; no consulta el estado de la cuenta en Users Service. Por ello, la revocación o cambio de permisos depende de que el token usado por el cliente refleje los claims actualizados y de la validación de Firebase.

## 3. Resumen de endpoints

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| `GET` | `/` | Listar eventos. | Público, con filtrado de visibilidad. |
| `POST` | `/` | Crear un evento. | `SYS_ADMIN` o `EVENT_ADMIN`. |
| `GET` | `/<pk>/` | Consultar un evento por ID. | Público, sujeto a visibilidad. |
| `PATCH` | `/<pk>/` | Actualizar parcialmente un evento. | `SYS_ADMIN` o `EVENT_ADMIN`; propietario, salvo `SYS_ADMIN`. |
| `DELETE` | `/<pk>/` | Eliminar un evento. | `SYS_ADMIN` o `EVENT_ADMIN` y propietario; los publicados solo los elimina `SYS_ADMIN`. |
| `POST` | `/<pk>/publish/` | Publicar un evento. | `SYS_ADMIN` o `EVENT_ADMIN`; propietario, salvo `SYS_ADMIN`. |
| `POST` | `/<pk>/subscribe/` | Suscribirse a un evento. | Público. |
| `POST` | `/<pk>/unsubscribe/` | Cancelar una suscripción. | Público. |

`pk` es el identificador primario del evento. Las rutas finales pueden incluir el prefijo con el que se registre esta aplicación en el proyecto principal.

## 4. Modelo de evento y formato JSON

La respuesta de `EventSerializer` contiene los siguientes campos:

| Campo | Tipo | Lectura / escritura | Descripción |
|---|---|---|---|
| `id` | entero | Solo lectura | Identificador del evento. |
| `name` | cadena | Escritura | Nombre; máximo 100 caracteres. |
| `description` | cadena | Escritura | Descripción del evento. |
| `published` | booleano | Solo lectura | Indica si está publicado. Se cambia mediante `POST /<pk>/publish/`. |
| `place` | cadena | Escritura | Lugar; máximo 200 caracteres. |
| `latitude` | decimal | Escritura | Latitud geográfica, hasta 7 decimales. |
| `longitude` | decimal | Escritura | Longitud geográfica, hasta 7 decimales. |
| `datetime_start` | fecha y hora | Escritura | Fecha y hora de inicio. |
| `datetime_end` | fecha y hora | Escritura | Fecha y hora de finalización. |
| `organizer` | objeto | Solo lectura en la respuesta | Contiene `id` (UID de Firebase del organizador) y `name`. |
| `status` | cadena | Escritura | Estado del evento: `PRO`, `ECU`, `CAN` o `FIN`. |
| `category` | cadena o `null` | Escritura | Categoría: `ACA`, `CUL`, `DEP`, `ASA`, `PAR`, `OTR`. |
| `image` | URL o `null` | Solo lectura | URL de la imagen almacenada. |
| `image_file` | archivo | Solo escritura | Imagen que se carga al crear o actualizar el evento. |
| `is_subscribed` | booleano | Solo lectura | Para un usuario autenticado, indica si su UID está suscrito. Para anónimos devuelve `false`. |

Ejemplo ilustrativo de un evento:

```json
{
  "id": 17,
  "name": "Feria de proyectos",
  "description": "Muestra de proyectos de la comunidad.",
  "published": true,
  "place": "Auditorio principal",
  "latitude": "4.6380000",
  "longitude": "-74.0840000",
  "datetime_start": "2026-11-15T14:00:00Z",
  "datetime_end": "2026-11-15T18:00:00Z",
  "organizer": {
    "id": "firebase-uid-del-organizador",
    "name": "Nombre Apellido"
  },
  "status": "PRO",
  "category": "ACA",
  "image": "https://example.com/events/feria.jpg",
  "is_subscribed": false
}
```

Los valores y la URL del ejemplo son ficticios. El formato exacto de las fechas depende de la configuración de Django REST Framework.

### Estados

| Código | Estado |
|---|---|
| `PRO` | Programado |
| `ECU` | En Curso |
| `CAN` | Cancelado |
| `FIN` | Finalizado |

### Categorías

| Código | Categoría |
|---|---|
| `ACA` | Académico |
| `CUL` | Cultural |
| `DEP` | Deportes |
| `ASA` | Asamblea |
| `PAR` | Parche |
| `OTR` | Otro |

## 5. Endpoints en detalle

### 5.1. Listar eventos

**`GET /`**

Devuelve los eventos visibles para el solicitante. El orden predeterminado es por `datetime_start` ascendente.

**Parámetros de consulta**

| Parámetro | Tipo | Descripción | Ejemplo |
|---|---|---|---|
| `category` | cadena | Filtra por categoría exacta. | `?category=ACA` |
| `category__in` | lista de valores | Filtra por varias categorías separadas por comas, según el backend de filtros. | `?category__in=ACA,CUL` |
| `status` | cadena | Filtra por estado exacto. | `?status=PRO` |
| `status__in` | lista de valores | Filtra por varios estados. | `?status__in=PRO,ECU` |
| `published` | booleano | Filtra por publicación. | `?published=true` |
| `published__in` | lista de valores | Filtra por varios valores de publicación. | `?published__in=true,false` |
| `search` | cadena | Busca coincidencias en `name` y `place`. | `?search=feria` |
| `ordering` | cadena | Ordena por `name` o `datetime_start`. Anteponer `-` invierte el orden. | `?ordering=-datetime_start` |

Los filtros `__in` siguen la sintaxis del backend de `django-filter`; la aceptación concreta de listas separadas por comas depende de la configuración instalada.

**Visibilidad según autenticación**

- Solicitud anónima: solo devuelve eventos con `published=true`.
- Solicitud autenticada: devuelve eventos publicados y eventos cuyo `organizer_id` coincide con el `uid` del token.
- Un evento no publicado de otro organizador no debe aparecer en la colección ni consultarse mediante detalle.

**Ejemplo**

```http
GET /?category=ACA&status=PRO&ordering=datetime_start
```

**Respuesta exitosa — `200 OK`**

La respuesta es una lista de eventos serializados. La estructura de paginación, si existe, depende de la configuración global de DRF.

### 5.2. Consultar un evento

**`GET /<pk>/`**

Devuelve el evento identificado por `pk`, siempre que sea visible para el solicitante.

```http
GET /17/
```

**Respuesta exitosa — `200 OK`:** objeto de evento según el formato de la sección 4.

Errores esperables:
- `404 Not Found`: el evento no existe o queda fuera del queryset visible para el solicitante.
- `401 Unauthorized`: el token enviado no puede validarse. La consulta sin token es pública.

### 5.3. Crear un evento

**`POST /`**

Requiere un token Firebase válido y un claim `role` igual a `SYS_ADMIN` o `EVENT_ADMIN`.

Campos de entrada habituales:

```json
{
  "name": "Feria de proyectos",
  "description": "Muestra de proyectos de la comunidad.",
  "place": "Auditorio principal",
  "latitude": "4.6380000",
  "longitude": "-74.0840000",
  "datetime_start": "2026-11-15T14:00:00Z",
  "datetime_end": "2026-11-15T18:00:00Z",
  "status": "PRO",
  "category": "ACA"
}
```

Los campos `organizer`, `published`, `image` e `is_subscribed` no se deben enviar para definir la identidad del organizador o publicar el evento: el organizador se toma del token (`uid` y `name`), `published` es de solo lectura y la imagen se gestiona mediante `image_file`.

Para cargar una imagen, usa `multipart/form-data` y envía los campos del evento junto con `image_file`. Sin archivo, el evento se crea sin imagen.

**Validaciones de fechas**

- En la creación, `datetime_start` no puede ser anterior a la hora actual.
- `datetime_end` debe ser posterior a `datetime_start`.

**Respuesta exitosa:** `201 Created`, con el evento serializado.

Errores esperables:
- `400 Bad Request`: campos inválidos o fechas inconsistentes.
- `401 Unauthorized`: token ausente cuando se necesita identidad o token inválido.
- `403 Forbidden`: rol sin autorización.

### 5.4. Actualizar parcialmente un evento

**`PATCH /<pk>/`**

Actualiza únicamente los campos enviados. Requiere rol `SYS_ADMIN` o `EVENT_ADMIN` y que el usuario sea el organizador; `SYS_ADMIN` puede actualizar eventos de otros organizadores.

Ejemplo:

```http
PATCH /17/
Content-Type: application/json
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

```json
{
  "description": "Descripción actualizada."
}
```

Se aplican las validaciones de fechas del serializador. Para reemplazar la imagen, envía `image_file` como `multipart/form-data`; el servicio elimina la imagen anterior e intenta cargar la nueva.

Un evento publicado que esté cancelado (`CAN`) o finalizado (`FIN`) no se puede modificar. Los eventos no publicados son editables independientemente de su estado según `is_editable()`.

**Respuesta exitosa:** `200 OK`, con el evento actualizado.

Errores esperables:
- `400 Bad Request`: datos inválidos.
- `401 Unauthorized`: token inválido.
- `403 Forbidden`: rol insuficiente, el solicitante no es propietario o el evento publicado está cancelado/finalizado.
- `404 Not Found`: evento inexistente o no visible para el solicitante.

### 5.5. Eliminar un evento

**`DELETE /<pk>/`**

Requiere rol `SYS_ADMIN` o `EVENT_ADMIN` y permiso de propietario, salvo que `SYS_ADMIN` puede superar la comprobación de propiedad. Hay una regla adicional: si el evento está publicado, solo `SYS_ADMIN` puede eliminarlo.

Cuando el evento tiene una imagen, el servicio intenta eliminarla del almacenamiento antes de borrar el registro.

**Respuesta exitosa:** `204 No Content`.

Errores esperables:
- `401 Unauthorized`: token inválido.
- `403 Forbidden`: permisos insuficientes o intento de un rol distinto de `SYS_ADMIN` de eliminar un evento publicado.
- `404 Not Found`: evento inexistente o no visible para el solicitante.

### 5.6. Publicar un evento

**`POST /<pk>/publish/`**

Publica un evento estableciendo `published=true`. Requiere rol `SYS_ADMIN` o `EVENT_ADMIN` y autorización de propietario, salvo `SYS_ADMIN`.

No es posible publicar:
- Un evento que ya está publicado.
- Un evento cancelado (`CAN`).
- Un evento finalizado (`FIN`).

**Solicitud**

```http
POST /17/publish/
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

No requiere cuerpo.

**Respuesta exitosa — `200 OK`:** evento serializado con `published=true`.

Errores esperables:
- `400 Bad Request`: el evento ya está publicado o está cancelado/finalizado.
- `401 Unauthorized`: token inválido.
- `403 Forbidden`: rol insuficiente o falta de propiedad.
- `404 Not Found`: evento inexistente o no visible.

### 5.7. Suscribirse a un evento

**`POST /<pk>/subscribe/`**

Es público y admite dos modalidades.

**A. Usuario autenticado**

Envía un token Firebase válido. La suscripción se asocia con `request.user.uid` y se utiliza el claim `email` como correo de notificación. No requiere cuerpo.

```http
POST /17/subscribe/
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

**B. Usuario anónimo**

Envía un correo en el cuerpo JSON:

```http
POST /17/subscribe/
Content-Type: application/json
```

```json
{
  "email": "persona@example.com"
}
```

El correo debe ser válido. El servicio evita suscripciones duplicadas por UID para usuarios autenticados y por correo para suscripciones anónimas.

Solo es posible suscribirse a eventos publicados y que no estén cancelados (`CAN`) ni finalizados (`FIN`).

**Respuesta exitosa — `201 Created`**

```json
{
  "detail": "Suscripción realizada correctamente."
}
```

Errores esperables:
- `400 Bad Request`: correo inválido, evento no publicado, evento cancelado/finalizado o suscripción duplicada.
- `404 Not Found`: evento inexistente o no visible.

### 5.8. Cancelar una suscripción

**`POST /<pk>/unsubscribe/`**

También es público y admite dos modalidades.

**A. Usuario autenticado**

La suscripción se busca por el UID del token. No requiere cuerpo.

```http
POST /17/unsubscribe/
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

**B. Usuario anónimo**

Debe enviar el correo que se utilizó al suscribirse:

```http
POST /17/unsubscribe/
Content-Type: application/json
```

```json
{
  "email": "persona@example.com"
}
```

Si existe la suscripción correspondiente, se elimina.

**Respuesta exitosa — `200 OK`**

```json
{
  "detail": "Suscripción cancelada correctamente."
}
```

Errores esperables:
- `400 Bad Request`: falta el correo requerido o no existe una suscripción para ese UID/correo.
- `404 Not Found`: evento inexistente o no visible.

## 6. Códigos HTTP habituales

| Código | Significado en este servicio |
|---|---|
| `200 OK` | Consulta, actualización, publicación o cancelación de suscripción exitosa. |
| `201 Created` | Evento o suscripción creados correctamente. |
| `204 No Content` | Evento eliminado correctamente. |
| `400 Bad Request` | Validación fallida, transición de estado no permitida o suscripción duplicada/no existente. |
| `401 Unauthorized` | Firebase ID token inválido o no verificable cuando la autenticación es necesaria. |
| `403 Forbidden` | Rol o propiedad insuficientes, o regla de negocio que impide la operación. |
| `404 Not Found` | Recurso inexistente o excluido del queryset visible. |

La respuesta exacta puede variar según la configuración global de excepciones y autenticación de Django REST Framework.

## 7. Consideraciones de integración y observaciones de implementación

1. **No existe una llamada directa a Users Service.** El identificador compartido es el UID de Firebase (`uid`), no el ID entero de la base de datos del Users Service. No se deben comparar ni intercambiar esos identificadores.
2. **El rol proviene del token.** Users Service mantiene el rol en su base de datos y lo publica como claim personalizado `role`. Tras cambiar un rol, el frontend debe renovar el token cuando el endpoint de login indique `claims_changed=true`.
3. **Claims faltantes:** `TokenUser` accede a `claims["uid"]`; si el token verificado no incluye `uid`, podría producirse un error no controlado. Los tokens Firebase ID normales incluyen ese identificador, pero conviene mantener el contrato explícito.
4. **Tokens inválidos en rutas públicas:** si se envía un encabezado `Bearer` con un token inválido, la autenticación falla incluso en una operación marcada como pública. Para acceso anónimo, no envíes un token vencido o inválido.
5. **`PUT` no está habilitado.** `EventViewSet.http_method_names` solo permite `get`, `post`, `patch` y `delete`; por tanto, las actualizaciones deben hacerse con `PATCH`.
6. **Carga de imágenes:** el servicio delega la carga y eliminación en `core.firebase_storage`. La disponibilidad y el formato de la URL dependen de la configuración de ese almacenamiento.
7. **Suscripciones anónimas y autenticadas son identidades diferentes.** Una suscripción anónima se identifica por correo; una autenticada, por UID y por el correo de notificación del token. No se observa una operación automática que fusione ambas si una persona se registra después.
8. **Validación del correo en suscripciones anónimas:** el serializador valida sintaxis de email, pero no exige el dominio `@unal.edu.co`. Esa restricción sí aparece en la autenticación del Users Service, no en este endpoint público.
9. **`is_subscribed` solo refleja suscripciones por UID.** Para usuarios anónimos siempre devuelve `false`, porque no hay identidad autenticada con la que buscar la suscripción.
10. **Visibilidad del evento:** el queryset de detalle también aplica las reglas de publicación/propiedad. Por eso, un evento no publicado de otro organizador puede responder `404` en lugar de `403`.
11. **Errores de almacenamiento:** la vista borra la imagen anterior antes de cargar el reemplazo. Si la nueva carga falla, la imagen anterior ya podría haberse eliminado; conviene tenerlo en cuenta al robustecer el flujo de actualización.

