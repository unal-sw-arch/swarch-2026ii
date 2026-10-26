# Documentación de la API — Users Service

## 1. Descripción general

El servicio **Users Service** expone endpoints para sincronizar los datos de identidad del usuario autenticado, consultar su perfil y administrar usuarios registrados.

La API está implementada con Django REST Framework. Las rutas de colección y detalle son generadas por un `DefaultRouter` para `UserViewSet`; los endpoints `login/` y `me/` se declaran explícitamente.

> **Base URL:** sustituye `<BASE_URL>` por la URL raíz donde esté desplegado el servicio. Las rutas descritas son relativas a esa base y pueden estar precedidas por un prefijo configurado en el proyecto principal.

## 2. Autenticación y autorización

Los endpoints documentados requieren autenticación. La autorización depende del rol del usuario:

| Rol | Permisos según las vistas |
|---|---|
| Administrador del Sistema (`SYS_ADMIN`) | Listar y consultar detalles; modificar y eliminar usuarios, con restricciones para cuentas de Administrador del Sistema. |
| Administrador de Implementos (`IMPLEMENT_ADMIN`) | Listar y consultar detalles; en actualizaciones parciales, solo puede modificar `status`. |
| Otros roles autenticados | Pueden consultar su propio perfil mediante `GET /me/`; no tienen permiso para las operaciones de `UserViewSet`. |

Las vistas aplican `IsAuthenticated`. Para listar y consultar detalles, `UserViewSet` exige además `IsSystemAdminOrImplementAdmin`; para actualizar o eliminar, exige `IsSystemAdmin`.

La forma concreta de enviar las credenciales depende del mecanismo de autenticación configurado en el proyecto (por ejemplo, sesión, token o JWT). Ese mecanismo no se define en los archivos revisados.

## 3. Resumen de endpoints

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/login/` | Sincronizar los claims del token con el usuario autenticado y devolver su ID y rol. | Cualquier usuario autenticado. |
| `GET` | `/me/` | Consultar el perfil del usuario autenticado. | Cualquier usuario autenticado. |
| `GET` | `/` | Listar usuarios. | Administrador del Sistema o Administrador de Implementos. |
| `GET` | `/<pk>/` | Consultar un usuario por ID. | Administrador del Sistema o Administrador de Implementos. |
| `PATCH` | `/<pk>/` | Actualizar parcialmente un usuario. | Administrador del Sistema; el Administrador de Implementos tiene restricciones adicionales según la configuración de permisos. |
| `DELETE` | `/<pk>/` | Eliminar un usuario. | Administrador del Sistema, excepto si el usuario objetivo es Administrador del Sistema. |

`UserViewSet` incluye los mixins de listado, detalle, actualización y eliminación, pero no incluye creación. Por tanto, no se expone un endpoint `POST` para crear usuarios a través de este ViewSet.

## 4. Endpoints en detalle

### 4.1. Sincronizar la información de inicio de sesión

**`POST /login/`**

Este endpoint se utiliza después de que el mecanismo de autenticación haya validado las credenciales y asociado la solicitud con un usuario. Llama a `sync_claims()` para publicar en Firebase Authentication el claim personalizado `role` correspondiente al rol actual del usuario. Si el usuario no tiene `firebase_uid`, la función no modifica Firebase y devuelve `false`. Si tiene un UID, compara el claim `role` del token actual con el rol de la base de datos: cuando coinciden, no realiza cambios y devuelve `false`; si no coinciden —o no se proporcionaron claims actuales—, publica los claims en Firebase y devuelve `true`.

La vista no requiere un cuerpo de solicitud específico. La autenticación debe proporcionar `request.user` y `request.auth`; el formato de las credenciales depende de la configuración de autenticación del proyecto.

**Ejemplo de solicitud**

```http
POST /login/
Authorization: <credenciales según la configuración de autenticación>
```

No se requiere un cuerpo JSON específico para esta vista.

**Respuesta exitosa — `200 OK`**

```json
{
  "id": 42,
  "role": "COMMUNITY",
  "claims_changed": true
}
```

Los valores son ilustrativos. `claims_changed` es `true` cuando se publicaron claims personalizados en Firebase y `false` cuando no fue necesario hacerlo o el usuario no tiene `firebase_uid`. Si devuelve `true`, el cliente debe renovar el token para obtener los claims actualizados.

Campos de respuesta:

| Campo | Tipo esperado | Descripción |
|---|---|---|
| `id` | entero | Identificador del usuario autenticado. |
| `role` | cadena | Rol actual del usuario, por ejemplo `SYS_ADMIN`, `IMPLEMENT_ADMIN`, `EVENT_ADMIN` o `COMMUNITY`. |
| `claims_changed` | booleano | Indica si se publicaron claims en Firebase. Si es `true`, el cliente debe renovar el token para recibir el rol actualizado. |

**Comportamiento de sincronización**

- Si el usuario no tiene `firebase_uid`, la respuesta incluye `"claims_changed": false` y no se actualizan claims en Firebase.
- Si el claim `role` del token actual ya coincide con el rol del usuario, la respuesta incluye `"claims_changed": false`.
- Si el claim `role` no coincide, o no hay claims actuales disponibles para comparar, se publica `{"role": "<rol actual>"}` en Firebase y la respuesta incluye `"claims_changed": true`. El cliente debe renovar el token para recibir los claims actualizados.

**Errores habituales**

- `401 Unauthorized`: la solicitud no está autenticada o las credenciales no son válidas.
- Un error al comunicarse con Firebase puede producirse si `set_custom_user_claims()` falla; el resultado concreto depende de la configuración global de excepciones.

### 4.2. Obtener el perfil del usuario autenticado

**`GET /me/`**

Devuelve algunos datos del usuario asociado a la solicitud autenticada. No recibe parámetros de ruta ni de consulta.

**Respuesta exitosa — `200 OK`**

```json
{
  "id": 42,
  "email": "usuario@unal.edu.co",
  "name": "Nombre Apellido",
  "faculty": "ING",
  "status": "ACT",
  "roles": "COMMUNITY"
}
```

Los valores son ilustrativos. El campo `roles` contiene el valor de `request.user.role` (un único rol), aunque su nombre esté en plural. La vista obtiene también los nombres de los grupos del usuario, pero actualmente no los incluye en la respuesta.

Campos:

| Campo | Tipo esperado | Descripción |
|---|---|---|
| `id` | entero | Identificador del usuario. |
| `email` | cadena | Correo electrónico. |
| `name` | cadena | Nombre del usuario. |
| `faculty` | cadena o `null` | Código de facultad, si está asignada. |
| `status` | cadena | Estado del usuario: `ACT` (Activo) o `SAN` (Sancionado). |
| `roles` | cadena | Rol del usuario, por ejemplo `SYS_ADMIN`, `IMPLEMENT_ADMIN`, `EVENT_ADMIN` o `COMMUNITY`. |

**Errores habituales**

- `401 Unauthorized`: la solicitud no está autenticada.

### 4.3. Listar usuarios

**`GET /`**

Devuelve la colección de usuarios, ordenada por `date_joined` ascendente (los más antiguos primero).

**Permisos:** Administrador del Sistema o Administrador de Implementos.

**Parámetros de consulta**

| Parámetro | Tipo | Descripción | Ejemplo |
|---|---|---|---|
| `faculty` | cadena | Filtra por código exacto de facultad. | `?faculty=ING` |
| `role` | cadena | Filtra por rol exacto. | `?role=COMMUNITY` |
| `status` | cadena | Filtra por estado exacto. | `?status=ACT` |
| `search` | cadena | Busca por correo o nombre, si el backend tiene habilitado el filtro de búsqueda. | `?search=ana` |
| `format` | cadena | Sufijo opcional de formato proporcionado por DRF, si se usa. | `/.json` |

**Ejemplo**

```http
GET /?faculty=ING&status=ACT
```

**Respuesta exitosa — `200 OK`**

La respuesta utiliza `UserSerializer`. El formato concreto puede ser una lista JSON o una estructura paginada, según la configuración global de paginación de DRF. Un elemento tiene esta forma:

```json
{
  "id": 42,
  "email": "usuario@unal.edu.co",
  "name": "Nombre Apellido",
  "faculty": "ING",
  "status": "ACT",
  "is_active": true,
  "role": "COMMUNITY",
  "date_joined": "2026-09-01T10:30:00Z"
}
```

Los valores son ilustrativos. `id`, `email`, `name` y `date_joined` son de solo lectura en el serializador.

**Nota de implementación:** en el código suministrado aparece `search_filters = ["email", "name"]`. DRF espera el atributo `search_fields`, no `search_filters`, para configurar `SearchFilter`. Si no se define `search_fields` en otro lugar, el parámetro `search` no buscará esos campos como se pretende. Conviene cambiarlo a `search_fields = ["email", "name"]`.

### 4.4. Consultar un usuario por ID

**`GET /<pk>/`**

Devuelve la información de un usuario identificado por su clave primaria (`pk`, correspondiente al ID).

**Permisos:** Administrador del Sistema o Administrador de Implementos.

**Parámetro de ruta**

| Parámetro | Tipo | Descripción |
|---|---|---|
| `pk` | entero | ID del usuario que se desea consultar. |

**Ejemplo**

```http
GET /42/
```

**Respuesta exitosa — `200 OK`**

```json
{
  "id": 42,
  "email": "usuario@unal.edu.co",
  "name": "Nombre Apellido",
  "faculty": "ING",
  "status": "ACT",
  "is_active": true,
  "role": "COMMUNITY",
  "date_joined": "2026-09-01T10:30:00Z"
}
```

**Errores habituales**

- `401 Unauthorized`: solicitud no autenticada.
- `403 Forbidden`: el usuario autenticado no cuenta con el rol requerido.
- `404 Not Found`: no existe un usuario con ese ID.

### 4.5. Actualizar parcialmente un usuario

**`PATCH /<pk>/`**

Actualiza parcialmente los campos permitidos por el serializador y las reglas de permisos. Se deben enviar únicamente los campos que se desean modificar.

**Parámetro de ruta**

| Parámetro | Tipo | Descripción |
|---|---|---|
| `pk` | entero | ID del usuario que se desea actualizar. |

**Campos del serializador**

| Campo | Modificable por el serializador | Observaciones |
|---|---|---|
| `id` | No | Solo lectura. |
| `email` | No | Solo lectura. |
| `name` | No | Solo lectura. |
| `faculty` | Sí | Si el rol es `IMPLEMENT_ADMIN`, se exige que haya una facultad asignada. |
| `status` | Sí | `ACT` o `SAN`. |
| `is_active` | Sí | Campo heredado de Django incluido en el serializador. |
| `role` | Sí | Rol del usuario, sujeto a permisos y reglas de la vista. |
| `date_joined` | No | Solo lectura. |

**Reglas adicionales de la vista**

- Si el usuario objetivo tiene el rol `SYS_ADMIN`, la actualización se rechaza con `403 Forbidden` y el mensaje `No se puede modificar a un Administrador del Sistema.`
- Un usuario con rol `IMPLEMENT_ADMIN` solo puede enviar el campo `status`. Si incluye otros campos, la solicitud se rechaza con `403 Forbidden` y el mensaje `El Administrador de Implementos solo puede modificar el estado del usuario.`
- La validación del serializador exige una facultad cuando el rol resultante es `IMPLEMENT_ADMIN`.
- Aunque `UserViewSet` define `partial_update`, `get_permissions()` asigna `IsSystemAdmin()` a las acciones distintas de `list` y `retrieve`. Por ello, el Administrador de Implementos no puede alcanzar normalmente esta acción: el permiso de la vista lo rechaza antes de aplicar su restricción específica. Para permitirle cambiar `status`, habría que ajustar `get_permissions()` para autorizar explícitamente esa acción.

**Ejemplo de solicitud**

```http
PATCH /42/
Content-Type: application/json
```

```json
{
  "status": "SAN"
}
```

**Respuesta exitosa — `200 OK`**

Devuelve el objeto actualizado con los campos del `UserSerializer`.

**Errores habituales**

- `400 Bad Request`: datos no válidos, por ejemplo, asignar el rol `IMPLEMENT_ADMIN` sin facultad.
- `401 Unauthorized`: solicitud no autenticada.
- `403 Forbidden`: permisos insuficientes o una de las restricciones anteriores.
- `404 Not Found`: no existe un usuario con ese ID.

### 4.6. Eliminar un usuario

**`DELETE /<pk>/`**

Elimina el usuario identificado por `pk`.

**Permisos:** Administrador del Sistema.

**Ejemplo**

```http
DELETE /42/
```

**Respuesta exitosa**

`204 No Content`, sin cuerpo de respuesta.

**Regla de protección**

No se permite eliminar una cuenta cuyo rol sea `SYS_ADMIN`. En ese caso, la API responde `403 Forbidden` con el mensaje `No se puede eliminar a un Administrador del Sistema.`

**Errores habituales**

- `401 Unauthorized`: solicitud no autenticada.
- `403 Forbidden`: permisos insuficientes o intento de eliminar un Administrador del Sistema.
- `404 Not Found`: no existe un usuario con ese ID.

## 5. Códigos de estado HTTP

| Código | Significado |
|---|---|
| `200 OK` | Consulta o actualización exitosa. |
| `204 No Content` | Eliminación exitosa. |
| `400 Bad Request` | Los datos enviados no superan la validación. |
| `401 Unauthorized` | Falta autenticación válida. |
| `403 Forbidden` | El usuario autenticado no tiene permisos o se incumple una regla de acceso. |
| `404 Not Found` | No existe el recurso solicitado. |

Los códigos anteriores describen los resultados esperables de DRF para estos casos; la respuesta exacta puede depender de la configuración global de excepciones y autenticación.

## 6. Valores de referencia

### Facultades

| Código | Facultad |
|---|---|
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

### Estados

| Código | Estado |
|---|---|
| `ACT` | Activo |
| `SAN` | Sancionado |

### Roles

| Código | Rol |
|---|---|
| `SYS_ADMIN` | Administrador del Sistema |
| `EVENT_ADMIN` | Administrador de Eventos |
| `IMPLEMENT_ADMIN` | Administrador de Implementos |
| `COMMUNITY` | Miembro de la Comunidad |

## 7. Consideraciones de implementación

1. **Filtro de búsqueda:** cambiar `search_filters` por `search_fields` en `UserViewSet` si se desea que `?search=` busque por `email` y `name`.
2. **Permisos del Administrador de Implementos:** el comentario de `partial_update` indica que puede cambiar el estado, pero `get_permissions()` solo concede actualización al Administrador del Sistema. Hay que alinear la configuración de permisos con la regla de negocio esperada.
3. **Actualización completa (`PUT`):** `UpdateModelMixin` también expone la acción `PUT /<pk>/`. Sin embargo, el código personalizado restringe los campos en `partial_update` (`PATCH`), no en `update` (`PUT`). Si se permite `PUT`, conviene aplicar controles equivalentes a esa acción o deshabilitarla si no se necesita. Además, los campos de solo lectura no se actualizan desde el serializador.
4. **Campo `roles` de `/me/`:** la respuesta usa `roles` para devolver `request.user.role`, que es un único valor. Si el contrato espera varios roles o grupos, debe ajustarse la respuesta.
5. **Creación de usuarios:** no se expone `POST /` mediante el `UserViewSet` actual. Si el servicio debe crear usuarios, se necesita implementar y autorizar esa operación por separado.
6. **Prefijo de rutas:** `show_urls` muestra rutas relativas de la app. La URL final depende de cómo se incluya `users.urls` en las rutas del proyecto principal.
