# Panadería Express

## 1. Equipo

**Nombre del equipo:** PanTech

| Integrante |
|---|
| Jaime Darley Angulo Tenorio |
| Diego Fernando Waitoto Quintero |
| Yeison Camilo Aparicio Aponte |
| Axel Gómez Moreno |

## 2. Sistema de software

### Nombre

**Panadería Express**

### Logo

![Logo Panadería Express](docs/logo.png)

### Descripción

Panadería Express resuelve el problema de la atención en una panadería con dos tipos de cliente: quien pide un café y un pan de vitrina y quiere salir rápido (**para llevar**), y quien se sienta a pedir un desayuno preparado que toma tiempo (**consumo en sitio**). Como los productos tienen tiempos de preparación muy distintos, atender en estricto orden de llegada (FIFO) deja esperando de más a los pedidos rápidos. El sistema organiza la cola de cocina según los tiempos estimados de preparación.

**Usuario final:** en el Prototipo 1 el empleado de la panadería es el único usuario: el cliente no interactúa con el sistema, pide verbalmente y el empleado registra el pedido. Hay cuatro roles dentro de la misma aplicación web:

- **Mesero:** crea pedidos (para llevar o en mesa), consulta el menú y gestiona las reservas.
- **Cocinero:** ve la cola de cocina en orden de prioridad y cambia el estado de las comandas.
- **Despachador:** confirma la entrega y retira la orden.
- **Productos (administración del catálogo):** mantiene el CRUD de productos y sus minutos de preparación base.

Además de las vistas por rol, la Web App incluye una vista **Historial** para consultar los tiempos de las comandas (Requerimiento 9).

> **Prototipo 2 (propuesto):** se agrega el rol `cliente`. Los roles de empleado pasan a ser `mesero`, `cocinero`, `despachador`, `supervisor` y `admin`, y la vista *Historial* deja de ser un rol para convertirse en un permiso de lectura. Cada empleado tiene su propia cuenta y puede tener uno o varios roles. El cliente se registra en la misma Web App, consulta el menú, **reserva una mesa**, crea pedidos **para llevar**, paga de forma simulada y sigue el estado de sus pedidos. También puede consultar y cancelar sus propias reservas. El personal conserva las vistas de cocina, despacho, catálogo e historial; el cliente no accede a ellas.

#### Alcance del Prototipo 1

El Prototipo 1 es el flujo básico funcional: un empleado registra un pedido, selecciona los productos, la comanda aparece en cocina, el personal actualiza su estado y finalmente se confirma la entrega. Además, el sistema conserva el **historial de estados** de cada comanda (Requerimiento 9) para consultar los tiempos de preparación y entrega.

| N.º | Requerimiento | Prioridad | Alcance | ¿Implementado en el Prototipo 1? |
|---|---|---|---|---|
| 1 | Crear pedido para llevar o en mesa | P1 | Implementar | Sí |
| 2 | Crear cita de reserva | P1 | Implementar | Sí |
| 3 | Elegir productos de vitrina o preparados | P1 | Implementar | Sí |
| 4 | Ver menú y tiempos estimados | P1 | Implementar | Sí |
| 6 | Cambiar estado a *En preparación* o *Listo* | P1 | Implementar | Sí |
| 7 | Confirmar entrega y retirar la orden | P1 | Implementar | Sí |
| 8 | CRUD de productos (minutos de preparación base) | P1 | Operaciones básicas | Sí |
| 9 | Registrar y consultar el historial de estados de cada comanda | P1 | Implementar | Sí |

> El detalle de la implementación de cada requerimiento está en la tabla de trazabilidad.

Para la cola de cocina se usa una regla sencilla basada en los tiempos estimados de preparación (los productos más rápidos se atienden primero). El algoritmo de priorización más completo, las alertas de espera, la pantalla de despacho avanzada, la actualización en tiempo real con WebSocket y las métricas quedan para los siguientes prototipos.

#### Alcance del Prototipo 2 (propuesto)

El Prototipo 2 se enfoca en tres frentes: **autenticación, autorización y jerarquía de usuarios**, **autoservicio del cliente (reservas, pedidos y pago simulado)** y **priorización avanzada de la cola de cocina**. Además, recibe el Requerimiento 5, que no se implementó en el Prototipo 1. Los requerimientos se documentan como diseño objetivo; **aún no están implementados**.

**Requerimientos funcionales nuevos**

| N.º | Requerimiento | Prioridad | Alcance | Estado |
|---|---|---|---|---|
| 10 | Registro, inicio de sesión y sesión de usuario | P2 | Implementar | Pendiente. Auth Service con usuarios, credenciales y JWT. El registro público crea usuarios con rol `cliente`; las cuentas de personal las crea un `admin`. |
| 11 | Autorización por roles y protección de endpoints | P2 | Implementar | Pendiente. Roles `cliente`, `mesero`, `cocinero`, `despachador`, `supervisor` y `admin`; permisos por rol, cuentas multi-rol y validación de alcance. Historial como permiso de lectura. |
| 12 | Priorización avanzada de la cola | P2 | Implementar | Pendiente. Ya existe SJF con desempate FIFO; faltan envejecimiento (aging), capacidad de cocina y ETA. |
| 13 | Registro público y autenticación de clientes | P2 | Implementar | Pendiente. El formulario público solo crea el rol `cliente`; los roles de personal los asigna un `admin` (o un `supervisor` para cuentas operativas) o los datos semilla. |
| 14 | Autoservicio del cliente: menú, pedido para llevar y pago | P2 | Implementar | Pendiente. El cliente arma su pedido y paga de forma simulada; el total se calcula en el servidor. |
| 15 | Seguimiento del estado del pedido por parte del cliente | P2 | Implementar | Pendiente. El cliente consulta por polling el estado de sus propios pedidos a través del Order Manager. |
| 16 | Historial de pedidos del cliente, limitado a sus propios pedidos | P2 | Implementar | Pendiente. El cliente solo ve sus pedidos; el personal autorizado ve el historial general. |
| 17 | Reservar mesa desde la cuenta del cliente | P2 | Implementar | Pendiente. El cliente crea, consulta y cancela sus propias reservas (fecha, hora, número de personas y notas); solo ve las suyas. La reserva guarda `customer_id` y un estado `pendiente`, `confirmada` o `cancelada`. |
| 18 | Gestión de cuentas de empleados y jerarquía | P2 | Implementar | Pendiente. El `admin` crea, desactiva y asigna uno o varios roles a las cuentas del personal; el `supervisor` puede gestionar cuentas operativas si tiene el permiso. Las cuentas no se eliminan y toda asignación queda auditada. |

**Requerimientos del Prototipo 1 que pasan al Prototipo 2**

| N.º | Requerimiento | Prioridad | Estado |
|---|---|---|---|
| 5 | Cobrar / confirmar pago y enviar comanda | P2 | Pendiente. El envío de la comanda a cocina ya está implementado; falta el cobro/confirmación de pago, que en el Prototipo 2 incluye el pago simulado del cliente y el cobro por `mesero`, `supervisor` o `admin`. |

**Requerimientos funcionales modificados**

| N.º | Requerimiento | Cambio para el Prototipo 2 |
|---|---|---|
| 1 | Crear pedido para llevar o en mesa | Admite pedidos creados por `cliente` (solo `para_llevar`) y por `mesero`, `supervisor` o `admin` (para llevar o en mesa). El pedido guarda `customer_id` tomado del JWT, nunca del cuerpo de la petición. |
| 2 | Crear cita de reserva | El `cliente` puede crear, consultar y cancelar sus propias reservas; `mesero`, `supervisor` o `admin` gestionan todas. La reserva guarda `customer_id` tomado del JWT y un estado `pendiente`, `confirmada` o `cancelada`. |
| 3 | Elegir productos de vitrina o preparados | Exige sesión autenticada; sigue siendo una operación de lectura. |
| 4 | Ver menú y tiempos estimados | El menú lo consultan el `cliente` y todo el personal autenticado. |
| 5 | Cobrar / confirmar pago y enviar comanda | El pago puede ser simulado por el cliente dueño del pedido o confirmado por `mesero`, `supervisor` o `admin`. El total se calcula en el servidor y el pedido se encola **solo después** de confirmar el pago. |
| 6 | Cambiar estado a *En preparación* o *Listo* | Exige rol `cocinero`, `supervisor` o `admin`; valida capacidad de cocina si se implementa el scheduler avanzado. |
| 7 | Confirmar entrega y retirar la orden | Exige rol `despachador`, `supervisor` o `admin`. |
| 8 | CRUD de productos (minutos de preparación base) | Exige rol `admin`. |
| 9 | Registrar y consultar el historial de estados | Historial deja de ser un rol y pasa a ser un permiso de lectura. El `cliente` solo consulta sus propios pedidos; el personal autorizado (`supervisor` o `admin`) consulta el historial general. |

**Requerimientos no funcionales que cambian**

| Requerimiento | Cambio para el Prototipo 2 |
|---|---|
| Arquitectura distribuida | Se agrega `auth-service` y el sistema pasa de 5 a 6 componentes. La misma Web App sirve a clientes y empleados. |
| Seguridad | Autenticación JWT, autorización por roles, permisos por acción, validación de alcance, HTTPS para Auth Service y credencial de servicio para la comunicación interna. |
| Privacidad y propiedad de los datos | Un cliente solo puede consultar y pagar sus propios pedidos y gestionar sus propias reservas; la validación de propiedad se hace en el servidor a partir del JWT. |
| Jerarquía y alcance | Los roles tienen un nivel y un alcance de datos. `admin` ve todo; `supervisor` ve toda la operación; los operativos ven solo su función; el `cliente` ve solo lo propio. |
| Cuentas multi-rol | Una cuenta personal puede tener varios roles. Los permisos efectivos son la unión de sus roles y el nivel efectivo es el mayor. No se comparten cuentas. |
| Auditoría | Cada pago guarda `payment_method`, `total_amount`, `paid_at` y `paid_by`. También se auditan la asignación de roles, la desactivación de cuentas y las acciones sensibles. |
| Registro de usuarios | El registro público siempre crea el rol `cliente`; los roles de personal solo los asigna un `admin` o, para cuentas operativas, un `supervisor` con el permiso correspondiente. |
| Persistencia | Se agregan los volúmenes `postgres_data` y `redis_data`; Redis con AOF para conservar la cola activa. |
| Despliegue | Red interna `panaderia-net`; en producción no se publican `5432` ni `6379`. |
| Tipos de conector | Se agregan `HTTPS / REST`, `HTTP / REST + JWT`, `HTTP Polling + JWT` y credencial de servicio. |
| Actualización de la interfaz | Se mantiene **polling**; WebSocket/SSE queda como extensión futura. |

##### Roles, jerarquía y permisos

Los roles del Prototipo 2 no son pestañas de la interfaz: son **cuentas personales** con permisos y alcance. Una misma cuenta puede tener varios roles; los permisos efectivos son la unión de sus roles y el nivel efectivo es el mayor. `productos` e `historial` dejan de ser roles y pasan a ser permisos (`products:manage` y `history:read_all`).

**Jerarquía**

| Nivel | Rol | Tipo | Alcance | Responsabilidad |
|---:|---|---|---|---|
| 100 | `admin` | Empleado | Todo el sistema | Usuarios, roles, catálogo, reportes y configuración |
| 80 | `supervisor` | Empleado | Toda la operación | Supervisa el turno, consulta historial, cancela pedidos y cubre a los operativos |
| 50 | `mesero` | Empleado | Pedidos y reservas | Crea pedidos, cobra y gestiona reservas |
| 50 | `cocinero` | Empleado | Cola de cocina | Consulta la cola y cambia estados de preparación |
| 50 | `despachador` | Empleado | Pedidos listos | Confirma entregas |
| 10 | `cliente` | Externo | Solo lo propio | Menú, reservas, pedidos para llevar, pago y seguimiento |

Los tres roles operativos están en el mismo nivel jerárquico, pero tienen permisos distintos. La jerarquía no implica que un cocinero sea “superior” a un mesero: cada rol controla su función. `supervisor` no reemplaza a `admin`; supervisa la operación, pero no administra roles ni la configuración del sistema.

**Multi-rol**

Una cuenta personal puede tener varios roles. Por ejemplo, `mesero` + `despachador` permite crear y cobrar pedidos, además de confirmar entregas. No se crean cuentas compartidas: cada empleado tiene su propia cuenta y sus acciones quedan auditadas.

**Matriz de permisos**

| Permiso | cliente | mesero | cocinero | despachador | supervisor | admin |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Ver menú | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Crear y pagar pedido propio | ✓ | — | — | — | — | — |
| Ver sus propios pedidos | ✓ | — | — | — | — | — |
| Crear pedido de mostrador | — | ✓ | — | — | ✓ | ✓ |
| Cobrar pedido | — | ✓ | — | — | ✓ | ✓ |
| Ver y cambiar cola de cocina | — | — | ✓ | — | ✓ | ✓ |
| Ver pedidos listos y confirmar entrega | — | — | — | ✓ | ✓ | ✓ |
| Gestionar reservas propias | ✓ | — | — | — | — | — |
| Gestionar todas las reservas | — | ✓ | — | — | ✓ | ✓ |
| Ver historial propio | ✓ | — | — | — | — | — |
| Ver historial general y reportes | — | — | — | — | ✓ | ✓ |
| CRUD de productos | — | — | — | — | — | ✓ |
| Gestionar cuentas operativas | — | — | — | — | ✓ | ✓ |
| Gestionar usuarios, roles y configuración | — | — | — | — | — | ✓ |

**Modelo de datos de autorización**

```text
auth.users                 (id, email, password_hash, full_name, active, ...)
auth.roles                 (id, name, description, level, is_staff)
auth.permissions           (id, code, description)
auth.user_roles            (user_id, role_id, assigned_at, assigned_by)
auth.role_permissions      (role_id, permission_id)
auth.audit_log             (id, user_id, action, entity, entity_id, created_at)
```

**Cuentas y jerarquía**

- El registro público siempre crea `cliente`.
- El `admin` crea las cuentas del personal y asigna uno o varios roles.
- El `supervisor` puede gestionar cuentas operativas si tiene `users:manage_operational`, pero no puede crear `admin` ni modificar roles.
- Las cuentas no se eliminan: se desactivan para conservar historial y auditoría.
- Cada acción sensible registra quién la hizo y cuándo.

**Autorización en los servicios**

- El JWT incluye `sub`, `roles`, `permissions` y `level`.
- Order Manager y Queue Scheduler validan la firma y los permisos localmente, sin consultar al Auth Service en cada petición.
- Cada endpoint usa una dependencia tipo `require_permission("orders:create")`.
- Además del permiso, se valida el alcance: `own`, `shift`, `all` o `kitchen`.
- El frontend oculta vistas y botones según los permisos, pero la decisión real se valida en el backend.

##### Plan de implementación del Prototipo 2

1. Crear `auth-service` con el esquema `auth`, migraciones y datos semilla.
2. Implementar registro de clientes, login, JWT y permisos.
3. Proteger Order Manager y Queue Scheduler con `require_permission` y validación de alcance.
4. Reemplazar el selector libre de roles del frontend por login y rutas protegidas.
5. Agregar la administración de cuentas y roles para `admin`, y la gestión de cuentas operativas para `supervisor`.
6. Probar los casos de denegación: cocinero no cobra, mesero no cambia estados de cocina, cliente no ve pedidos ajenos y supervisor no gestiona roles.

> La trazabilidad de los requerimientos del Prototipo 2 se completará cuando se implementen. Por ahora solo se documenta el alcance y los cambios respecto al Prototipo 1.

#### Trazabilidad requerimiento → implementación

| N.º | Vistas (frontend) | Servicio y ruta (backend) | Lógica / persistencia |
|---|---|---|---|
| 1 | `frontend/src/views/MeseroView.tsx` | Order Manager — `POST /orders` | Valida productos y `table_number`; guarda en `orders`/`order_items` (PostgreSQL) y encola en cocina. |
| 2 | `frontend/src/views/MeseroView.tsx` | Order Manager — `GET/POST/PUT/DELETE /reservations` | Tabla `reservations` (PostgreSQL). |
| 3 | `frontend/src/views/MeseroView.tsx` | Order Manager — `GET /menu`, `GET /products` | `Product.category ∈ {vitrina, preparado}`. |
| 4 | `MeseroView.tsx`, `CocineroView.tsx`, `DespachadorView.tsx` | Order Manager — `GET /menu`; Queue Scheduler — `GET /queue`, `GET /dispatch` | `prep_minutes` por producto y `estimated_minutes` por comanda. |
| 6 | `frontend/src/views/CocineroView.tsx` | Queue Scheduler — `PATCH /queue/orders/{id}/status` | `priority.is_valid_transition` (`pendiente → en_preparación → listo`). |
| 7 | `frontend/src/views/DespachadorView.tsx` | Queue Scheduler — `POST /queue/orders/{id}/deliver` | Exige estado `listo`; sale del `ZSET` de Redis y el hash queda con TTL. |
| 8 | `frontend/src/views/ProductosView.tsx` | Order Manager — `GET/POST/PUT/DELETE /products` | CRUD de `products`; bloquea el borrado con pedidos asociados (409). |
| 9 | `frontend/src/views/HistorialView.tsx`, `CocineroView.tsx`, `DespachadorView.tsx` | Queue Scheduler — `GET /history`, `GET /history/orders/{id}`; registro en `POST /queue/orders`, `PATCH /queue/orders/{id}/status`, `POST /queue/orders/{id}/deliver` | Tabla `order_status_events` (PostgreSQL): un evento por transición con `changed_at`. |

> El Requerimiento 5 no aparece en esta trazabilidad porque se trasladó al Prototipo 2.

## 3. Estructuras arquitectónicas

### Estructura de Componentes y Conectores (C&C)

#### Vista C&C — Prototipo 1

![Vista C&C - Prototipo 1](docs/Vista%20CyC.png)

#### Vista C&C — Prototipo 2 (diseño objetivo)

![Vista C&C - Prototipo 2](docs/Vista%20CyC%20P2.png)

El diseño del Prototipo 2 conserva la estructura del Prototipo 1 y agrega:

- **Auth Service** (`:8002`) como sexto componente, encargado del registro, inicio de sesión, emisión de JWT y administración de usuarios, roles, permisos y jerarquía.
- **Cliente como nuevo actor:** se registra en la misma Web App, consulta el menú, reserva mesa, crea pedidos `para_llevar`, paga de forma simulada y sigue el estado de sus pedidos. También consulta y cancela sus propias reservas. No accede a la cola de cocina, al despacho, al catálogo ni al historial general.
- **Autenticación y autorización (JWT + RBAC):** Order Manager y Queue Scheduler validan el token localmente; los roles son `cliente`, `mesero`, `cocinero`, `despachador`, `supervisor` y `admin`, e Historial queda como permiso de lectura.
- **Cuentas de empleado y jerarquía:** cada empleado tiene una cuenta personal y puede tener varios roles. La jerarquía es `admin` > `supervisor` > `mesero` / `cocinero` / `despachador`; el `cliente` es un actor externo separado. `productos` e `historial` son permisos, no roles.
- **Pago simulado (Requerimiento 5):** la tabla `orders` guarda estado y datos de pago; el total se calcula en el servidor y el pedido se envía a cocina **solo después** de confirmar el pago.
- **Queue Scheduler con priorización avanzada:** SJF + envejecimiento (aging) + capacidad de cocina + ETA, manteniendo el desempate FIFO.
- **Mismo PostgreSQL con esquema `auth`** para usuarios, roles, permisos y jerarquía, sin agregar un contenedor de base de datos adicional.
- **Polling** como mecanismo de actualización; WebSocket/SSE queda como extensión futura.

> Los diagramas del Prototipo 2 representan el diseño objetivo. La implementación de estos cambios se realizará en la siguiente fase.

#### Vista de tiers — Prototipo 1

![Vista de tiers - Prototipo 1](docs/tiers_despliegue.png)

#### Vista de tiers — Prototipo 2 (diseño objetivo)

![Vista de tiers - Prototipo 2](docs/tiers_despliegue%20P2.png)

La misma Web App sirve a clientes y empleados mediante vistas por rol. No se agrega un contenedor de presentación adicional: siguen siendo **6 contenedores** (`frontend`, `auth-service`, `order-manager`, `queue-scheduler`, `postgres` y `redis`).

#### Diagrama de secuencia — pedido y pago del cliente (Prototipo 2)

![Secuencia - Pedido y pago del cliente](docs/secuencia_cliente_pedido.png)

El flujo muestra el registro o inicio de sesión, la consulta del menú, la creación de un pedido `para_llevar`, el pago simulado y el seguimiento del estado. El pedido se encola **después** de confirmar el pago y el cliente nunca accede directamente al Queue Scheduler ni a pedidos de otros clientes.

#### Diagrama de secuencia — reserva de mesa del cliente (Prototipo 2)

![Secuencia - Reserva de mesa del cliente](docs/secuencia_cliente_reserva.png)

El cliente crea, consulta y cancela sus propias reservas. La reserva queda asociada a su `customer_id` y a un estado (`pendiente`, `confirmada` o `cancelada`); el personal con rol `mesero`, `supervisor` o `admin` conserva la gestión general de todas las reservas.

#### Estilos arquitectónicos utilizados

- **Arquitectura en capas (3 capas / 3 tiers):** presentación, lógica y datos, cada una en su propio nivel de despliegue. Cada capa solo se comunica con la inmediatamente inferior.
- **Cliente-servidor:** la aplicación web (cliente) solicita servicios a los servicios de la capa lógica mediante HTTP.
- **REST (servicios sin estado):** la capa lógica expone recursos (`/orders`, `/queue`, `/products`, …) con una interfaz uniforme sobre HTTP (GET/POST/PUT/PATCH/DELETE). Los servicios no guardan estado en el proceso: el estado vive en los almacenes, por lo que pueden replicarse. Evidencia: `frontend/src/api.ts`, `order-manager/app/routers/` y `queue-scheduler/app/main.py`.
- **Orientado a servicios:** la lógica se divide en dos servicios independientes con responsabilidades distintas (gestión de pedidos y gestión de la cola), desplegados por separado y comunicados por HTTP.
- **Centrado en datos (repositorios):** dos almacenes de datos con roles distintos; uno relacional para datos persistentes y uno NoSQL para el estado temporal de la cola. El acceso a los almacenes se encapsula en repositorios (`order-manager/app/database.py`, `queue-scheduler/app/store.py`).
- **Consulta periódica (pull / polling):** el cliente consulta cada cierto tiempo el estado de la cola en lugar de recibir notificaciones del servidor. Se deja WebSocket para la siguiente entrega. Evidencia: `frontend/src/hooks.ts` (`usePolling`).
- **Interfaz de usuario basada en componentes:** la Web App se compone de vistas por rol y hooks reutilizables. En el Prototipo 1 el cambio de rol se resuelve por estado local; en el Prototipo 2 se resuelve con la sesión autenticada y los permisos. Evidencia: `frontend/src/App.tsx`, `frontend/src/views/` y `frontend/src/hooks.ts`.

#### Elementos arquitectónicos

**Componentes**

| Componente | Tipo | Tecnología | Puerto | Responsabilidad |
|---|---|---|---|---|
| Web App | Cliente web (presentación) | React + Vite (JS/TS) | 80 | Interfaz por permisos. En el Prototipo 1 sirve al personal: mesero, cocina, despacho, administración del catálogo e historial. En el Prototipo 2 también sirve al cliente: registro, menú, reservas, pedido, pago y seguimiento. El menú y las vistas se generan según los permisos de la cuenta. |
| Order Manager | Servicio (lógica) | Python + FastAPI | 8000 | Gestión de pedidos, reservas y catálogo de productos (CRUD con tiempos de preparación base). |
| Queue Scheduler | Servicio (lógica) | Python + FastAPI | 8001 | Gestión de la cola de cocina: ordena los pedidos con la regla de priorización, administra los estados de las comandas y persiste su historial. |
| Relational DB | Base de datos relacional (datos) | PostgreSQL | 5432 | Datos persistentes: productos, tiempos base, pedidos, reservas e historial de estados de las comandas. |
| NoSQL DB | Base de datos NoSQL clave-valor (datos) | Redis | 6379 | Cola activa de cocina y estados temporales de las comandas. |

> En el diseño del Prototipo 2 se agrega el **Auth Service** (FastAPI, `:8002`), que centraliza usuarios, roles, permisos, jerarquía y emisión de JWT. Order Manager y Queue Scheduler validan el token localmente. La misma Web App expone las vistas del cliente y del personal según los permisos.

**Actor externo**

| Actor | Descripción |
|---|---|
| Empleado (Mesero / Cocinero / Despachador / Supervisor / Administrador) | Usuario final del Prototipo 1. Interactúa con el sistema desde el navegador web. En el Prototipo 2 cada empleado tiene su propia cuenta y puede tener uno o varios roles. |
| Cliente (Prototipo 2) | Usuario final que se registra en la Web App, consulta el menú, reserva mesa, crea pedidos para llevar, paga de forma simulada y sigue el estado de sus pedidos. También consulta y cancela sus propias reservas. No accede a la cola de cocina, al despacho, al catálogo ni al historial general. |

**Conectores**

| N.º | Origen | Destino | Tipo de conector | Descripción |
|---|---|---|---|---|
| 1 | Web App | Order Manager (REST API :8000) | HTTP / REST | Crear pedidos y reservas, consultar el menú y administrar productos. |
| 2 | Web App | Queue Scheduler (Scheduler API :8001) | HTTP Polling | Consulta periódica de la cola de cocina, de los pedidos listos y del historial; también el cambio de estado de las comandas. |
| 3 | Order Manager | Queue Scheduler (Scheduler API :8001) | HTTP / REST | Encolar un pedido nuevo para cocina. |
| 4 | Order Manager | Relational DB (SQL :5432) | SQL sobre TCP | Persistir productos, pedidos y reservas. |
| 5 | Queue Scheduler | NoSQL DB (Redis :6379) | Protocolo Redis sobre TCP | Mantener la cola priorizada y los estados temporales. |
| 6 | Queue Scheduler | Relational DB (SQL :5432) | SQL sobre TCP | Leer los tiempos base de preparación y registrar el historial de estados de las comandas en `order_status_events`. |

> El Prototipo 2 agrega el conector Cliente → Web App (HTTPS) y los conectores Web App → Auth Service (HTTPS / REST) y Auth Service → PostgreSQL (SQL / TCP, esquema `auth`, usuarios, roles, permisos y jerarquía). También cambia el conector Order Manager → Queue Scheduler para usar una credencial de servicio y encolar solo pedidos pagados. El cliente consulta el estado y su historial a través del Order Manager, nunca directamente del Queue Scheduler. El detalle está en la Vista C&C y en los diagramas de secuencia del Prototipo 2.

#### Cumplimiento de los requisitos no funcionales

| Requisito | Cómo se cumple |
|---|---|
| Arquitectura distribuida | Cinco componentes desplegados en contenedores independientes que se comunican por red. En el diseño del Prototipo 2 se agrega un sexto componente (Auth Service). |
| Al menos un componente de presentación (web front-end) | Web App (React + Vite). |
| Al menos dos componentes de lógica | Order Manager y Queue Scheduler. |
| Al menos dos componentes de datos (relacional y NoSQL) | PostgreSQL y Redis. |
| Al menos dos tipos distintos de conectores basados en HTTP | HTTP / REST (petición-respuesta) y HTTP Polling (consulta periódica). |
| Al menos dos lenguajes de programación de propósito general | JavaScript/TypeScript (frontend) y Python (servicios). |
| Despliegue orientado a contenedores | Cada componente corre en su propio contenedor Docker, orquestados con Docker Compose. |

## 4. Prototipo

### Estructura del proyecto

```
swarch-2026ii/               # Repositorio del curso (sección 3)
└── g3-H-PanTech/            # Carpeta del equipo (Team 3-H · PanTech)
    ├── README.md
    ├── docker-compose.yml
    ├── .env.example
    ├── docs/                  # diagramas y logo
    ├── frontend/              # Web App (React + Vite)
    ├── order-manager/         # Servicio de pedidos y catálogo (FastAPI)
    └── queue-scheduler/       # Servicio de cola de cocina (FastAPI)
```

> En el diseño del Prototipo 2 se agrega `auth-service/` (registro, login y JWT). La carpeta se creará cuando se implemente.

### Instrucciones para desplegar el sistema localmente

**Requisitos previos**

- Git
- Docker y Docker Compose (v2)

**Pasos**

1. Clonar el repositorio y ubicarse en la carpeta del equipo:

   ```bash
   git clone https://github.com/unal-sw-arch/swarch-2026ii.git
   cd swarch-2026ii
   cd g3-H-PanTech
   ```

2. Construir y levantar todos los contenedores:

   ```bash
   docker compose up --build
   ```

3. Abrir el sistema en el navegador:

   | Servicio | URL |
   |---|---|
   | Web App | http://localhost |
   | Order Manager (documentación de la API) | http://localhost:8000/docs |
   | Queue Scheduler (documentación de la API) | http://localhost:8001/docs |

4. Para detener el sistema:

   ```bash
   docker compose down
   ```

   Para borrar también los datos guardados (volúmenes):

   ```bash
   docker compose down -v
   ```

### Limitaciones conocidas

- **El detalle de la comanda no se persiste en PostgreSQL.** El Queue Scheduler mantiene el detalle y el estado actual de las comandas en Redis; las comandas entregadas salen de las pantallas y se conservan en Redis con un TTL (1 hora por defecto, configurable con `DELIVERED_TTL_SECONDS`). Sin embargo, **el historial de estados sí se persiste** de forma durable en la tabla `order_status_events` (Requerimiento 9), por lo que los tiempos de preparación y entrega se conservan aunque expire el TTL. El Order Manager persiste el pedido base en PostgreSQL (`orders` / `order_items`) al crearlo.
- La cola priorizada vive en un `ZSET` de Redis con `score` = tiempo estimado; el desempate por hora de llegada se resuelve al construir la vista de cocina.
- El Prototipo 1 no incluye cobro/pago (Requerimiento 5, trasladado al Prototipo 2), priorización avanzada, alertas de tiempo, métricas, WebSocket/SSE ni autenticación.
- El **Prototipo 2** incorpora en su diseño autenticación JWT/RBAC, el Auth Service, la jerarquía de roles de empleado (`admin` > `supervisor` > operativos), cuentas multi-rol, el rol `cliente` con autoservicio y pago simulado, el seguimiento e historial de pedidos propios, priorización con aging/capacidad/ETA, persistencia de Redis y el cobro/confirmación de pago (Requerimiento 5); su implementación está pendiente.
- **Validación de pedidos (Requerimiento 1).** Un pedido `en_mesa` exige número de mesa; un pedido `para_llevar` no lo requiere, pero tampoco se rechaza si se envía un número de mesa.
- **En el Prototipo 1, el envío de la comanda a cocina (parte del Requerimiento 5) ya está implementado:** al crear un pedido, el Order Manager lo encola en el Queue Scheduler. Lo único pendiente de ese requerimiento es el cobro / confirmación de pago. En el Prototipo 2 el encolado se hará **después** de confirmar el pago.
