# Plan de trabajo — Axel Gómez Moreno

## Rol

**Borde, cocina y cliente de escritorio.** Soy el dueño del API Gateway, del Kitchen Service y del KDS/POS. Mi trabajo cubre la entrada única al sistema, la cola priorizada de cocina y la aplicación de escritorio que usan cocineros y despachadores.

| Campo | Valor |
|---|---|
| Rama personal | `g3-H/axel-borde-cocina` |
| Rama de integración | `g3-H/Pantech-P2` |
| Componentes propios | `api-gateway/`, `kitchen-service/`, `kds-pos/` |
| Lenguajes | Go (Gateway y Kitchen), Dart (KDS/POS) |
| Depende de | Jaime (Redis, PostgreSQL, RabbitMQ, red y contratos), Diego (JWKS y claims), Yeison (`order.paid` y pedidos) |
| Bloquea a | Diego (rutas protegidas), Yeison (encolado y `order.ready`), Jaime (smoke test) |

## Misión

Que todo el tráfico entre por un solo punto, que la cola de cocina priorice bien sin dejar pedidos viejos sin atender, y que cocineros y despachadores tengan una aplicación de escritorio clara para operar el turno.

## Responsabilidades

1. **API Gateway:** ruteo, validación de JWT, rate limiting, CORS y propagación de identidad.
2. **Kitchen Service:** cola priorizada con SJF, aging, capacidad, ETA y transiciones de estado.
3. **KDS/POS:** cola de cocina, cambio de estados, despacho y confirmación de entrega.
4. **Contratos del borde:** tabla de rutas, códigos de error y encabezados internos.
5. **Documentación:** OpenAPI, configuración de la cola y README de cada carpeta.

## Fases y tareas

### Fase 0 — Esqueleto y contratos (primera semana)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| A0.1 | Crear `api-gateway/` con Go y `net/http` o Chi | Proyecto que compila | `GET /health` responde 200 |
| A0.2 | Crear `kitchen-service/` con Go | Proyecto que compila | `GET /health` responde 200 |
| A0.3 | Crear `kds-pos/` con Flutter | Proyecto que arranca en escritorio | Abre una ventana con pantalla vacía |
| A0.4 | Crear los `Dockerfile` de Gateway y Kitchen | Imágenes multistage | Arrancan con variables de entorno |
| A0.5 | Publicar la tabla de rutas del Gateway | `api-gateway/README.md` | Cada prefijo apunta a un servicio |
| A0.6 | Acordar el contrato de JWT con Diego y Jaime | Claims y encabezados | El Gateway valida y propaga la identidad |

### Fase 1 — API Gateway (semanas 2–3)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| A1.1 | Ruteo por prefijo | `/auth/*`, `/orders/*`, `/reservations/*`, `/menu/*`, `/queue/*`, `/history/*`, `/reports/*` | Cada ruta llega al servicio correcto |
| A1.2 | Validación de JWT | Middleware RS256 con JWKS | Sin token devuelve 401; token vencido devuelve 401 |
| A1.3 | Propagación de identidad | `X-User-Id`, `X-Roles`, `X-Permissions`, `X-Level` | Los servicios reciben la identidad validada |
| A1.4 | Permisos generales | Tabla de permisos por prefijo | Un cliente no accede a `/queue`, `/history` ni `/reports` |
| A1.5 | CORS | Orígenes permitidos | Web SSR y KDS funcionan sin errores de CORS |
| A1.6 | Rate limiting | Redis por usuario e IP | Un abuso devuelve 429 |
| A1.7 | Errores uniformes | JSON con `code`, `message` y `request_id` | Todos los servicios responden con el mismo formato |
| A1.8 | Health y readiness | `/health` y `/ready` | Docker Compose puede esperar al Gateway |
| A1.9 | Pruebas | Ruteo, 401, 403 y 429 | Casos felices y de denegación |

**Reglas que debo respetar:**

- El Gateway es el **único** punto de entrada público. Los servicios de lógica no se publican.
- Valido la firma y los permisos generales, pero **cada servicio vuelve a validar** permiso y alcance.
- No reescribo el `customer_id`: lo tomo del JWT validado.
- El cliente nunca accede a Kitchen, al despacho, al catálogo ni al historial general.

### Fase 2 — Kitchen Service: cola priorizada (semanas 3–4)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| A2.1 | Migraciones del esquema de cocina | `prep_times`, `order_status_events`, `kitchen_capacity` | Se crean por migración |
| A2.2 | Encolar pedido pagado | `POST /queue/orders` | Idempotente por `order_id`; exige `X-Service-Token` |
| A2.3 | Prioridad SJF | Score por tiempo estimado de preparación | El pedido más rápido sale primero cuando hay empate de espera |
| A2.4 | Aging | Incremento de prioridad por tiempo en cola | Un pedido viejo no queda postergado indefinidamente |
| A2.5 | Capacidad | Límite de pedidos simultáneos | Si la cocina está llena, el pedido espera sin perderse |
| A2.6 | ETA | Estimación por posición y capacidad | La API devuelve minutos estimados |
| A2.7 | Cola en Redis | `ZSET` con score y hash de detalle | Persistencia AOF; sobrevive a un reinicio |
| A2.8 | Consultar cola | `GET /queue` | Orden estable y desempate FIFO |
| A2.9 | Cambiar estado | `PATCH /queue/orders/{id}/status` | `pendiente → en_preparacion → listo`; transiciones inválidas devuelven 409 |
| A2.10 | Confirmar entrega | `POST /queue/orders/{id}/deliver` | Exige `listo`; exige `despachador`, `supervisor` o `admin` |
| A2.11 | Evento `order.ready` | Productor RabbitMQ | Payload con `event_id`, `order_id`, `ready_at`, `occurred_at` |
| A2.12 | Historial de estados | Registro en PostgreSQL | Un evento por transición con `changed_at` y `changed_by` |
| A2.13 | Pruebas | SJF, aging, capacidad, transiciones y permisos | Casos felices y de denegación |

**Algoritmo de prioridad (propuesta):**

```text
score = prep_minutes - aging_factor * minutos_en_espera
desempate = hora de llegada (FIFO)
un pedido en preparación no compite por prioridad; ocupa capacidad
```

- `prep_minutes` se calcula desde los productos del pedido.
- `aging_factor` es configurable por variable de entorno.
- El score se recalcula al consultar la cola y al encolar.
- La capacidad máxima de cocina es configurable.
- El ETA se calcula con los pedidos en preparación más la posición en la cola.

### Fase 3 — KDS/POS de escritorio (semanas 4–5)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| A3.1 | Login del KDS | Pantalla de inicio de sesión | Solo `cocinero`, `despachador`, `supervisor` o `admin` |
| A3.2 | Almacenamiento del token | Almacenamiento seguro del sistema | No se guarda en texto plano ni en un archivo del proyecto |
| A3.3 | Cola de cocina | Pantalla con orden, tiempos, ETA y estado | Se actualiza por polling cada 3–5 s |
| A3.4 | Cambio de estado | Botones *En preparación* y *Listo* | Llama al Gateway, no a Kitchen directo |
| A3.5 | Pantalla de despacho | Pedidos listos | Solo `despachador`, `supervisor` o `admin` |
| A3.6 | Confirmar entrega | Botón *Entregar* | Llama a `POST /queue/orders/{id}/deliver` |
| A3.7 | Manejo de errores | 401, 403, 409 y red caída | Mensajes claros y reintento manual |
| A3.8 | Configuración | URL del Gateway por archivo o variable | No hay URLs hardcodeadas |
| A3.9 | Build de escritorio | `flutter build windows` | Se genera un ejecutable |
| A3.10 | Pruebas | Widget tests y pruebas manuales | Flujo de cocina y despacho completo |

### Fase 4 — Pruebas, documentación e integración (semana 6)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| A4.1 | Pruebas de carga ligera | Script contra `GET /queue` | Respuesta estable con 50 pedidos |
| A4.2 | Pruebas de seguridad | Token ajeno, rol insuficiente, servicio directo | Todo denegado |
| A4.3 | Documentación | README de Gateway, Kitchen y KDS | Rutas, variables y algoritmo documentados |
| A4.4 | Diagramas | C&C y secuencia al día | Coinciden con el código |
| A4.5 | Integración con Jaime | Smoke test y revisión de PR | Flujo completo en verde |

## Endpoints que expongo

### API Gateway (:8080)

| Prefijo | Destino | Permiso general |
|---|---|---|
| `/auth/*` | Identity Service | Público en login y registro; autenticado en el resto |
| `/menu/*` | Order Service | Autenticado |
| `/orders/*` | Order Service | `orders:*` |
| `/reservations/*` | Order Service | `reservations:*` |
| `/products/*` | Order Service | `products:manage` |
| `/queue/*` | Kitchen Service | `kitchen:read`, `kitchen:update` |
| `/history/*` | Reporting Service | `history:read_all` |
| `/reports/*` | Reporting Service | `reports:read` |

### Kitchen Service (:8001)

| Método | Ruta | Permiso | Notas |
|---|---|---|---|
| POST | `/queue/orders` | `X-Service-Token` | Idempotente por `order_id` |
| GET | `/queue` | `kitchen:read` | Orden por prioridad, aging y FIFO |
| GET | `/queue/orders/{id}` | `kitchen:read` | Detalle y ETA |
| PATCH | `/queue/orders/{id}/status` | `kitchen:update` | Transiciones válidas |
| POST | `/queue/orders/{id}/deliver` | `kitchen:deliver` | Exige estado `listo` |
| GET | `/queue/health` | Público interno | Estado del servicio |

## Contratos que debo respetar

### Encabezados internos

| Encabezado | Origen | Uso |
|---|---|---|
| `X-User-Id` | Gateway | Identidad validada |
| `X-Roles` | Gateway | Roles separados por coma |
| `X-Permissions` | Gateway | Permisos separados por coma |
| `X-Level` | Gateway | Nivel efectivo |
| `X-Service-Token` | Worker / servicios | Secreto compartido del prototipo |
| `X-Request-Id` | Gateway | Trazabilidad entre servicios |

### Eventos

| Evento | Rol | Payload mínimo |
|---|---|---|
| `order.paid` | Consumo indirecto (vía Worker) | `event_id`, `order_id`, `customer_id`, `total`, `paid_at` |
| `order.ready` | Publico | `event_id`, `order_id`, `ready_at`, `occurred_at` |

### Propiedad de datos

| Almacén | Qué guardo |
|---|---|
| PostgreSQL | `prep_times`, `order_status_events`, `kitchen_capacity` |
| Redis | Cola priorizada, ETA y caché de capacidad |
| RabbitMQ | Publico `order.ready` |
| Gateway + Redis | Rate limiting y caché de sesión |

## Dependencias

| Dependo de | Para |
|---|---|
| Jaime | Redis, PostgreSQL, RabbitMQ, variables, red y compose |
| Diego | JWKS, claims y matriz de permisos |
| Yeison | `order.paid` y pedidos pagados para encolar |

| Dependen de mí | Para |
|---|---|
| Diego | Rutas protegidas y validación de JWT |
| Yeison | Encolado y `order.ready` para cerrar el ciclo |
| Jaime | Smoke test y demostración |

## Definición de terminado

- El Gateway es el único punto de entrada y devuelve 401/403/429 correctamente.
- La cola prioriza con SJF, aging y desempate FIFO.
- La capacidad y el ETA se reflejan en la API y en el KDS.
- Las transiciones inválidas devuelven 409.
- El KDS permite cocinar, marcar listo y despachar.
- `order.ready` se publica y el pedido se cierra en el historial.
- El PR tiene pruebas, README y diagramas actualizados.

## Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| La cola se desordena con el aging | Recalcular score al consultar y probar con pedidos viejos |
| El KDS es nuevo para el equipo | Empezar con una pantalla simple y polling; sin WebSocket |
| El Gateway duplica validaciones | Centralizar la firma y delegar permisos finos a los servicios |
| Redis pierde la cola al reiniciar | Activar AOF y reconstruir desde PostgreSQL si hace falta |
| Flutter en Windows requiere SDK | Documentar la instalación y ofrecer una versión web del KDS como respaldo |

## Primer PR

**`g3-H/axel-borde-cocina`:** esqueleto de `api-gateway/` con ruteo y health, y `kitchen-service/` con `POST /queue/orders` y `GET /queue` sobre Redis. Sin KDS todavía.

## Lo que no hago

- No implemento login ni emisión de JWT; los valido.
- No implemento pedidos, pagos ni reservas; consumo sus APIs y eventos.
- No expongo Kitchen directamente al navegador; todo pasa por el Gateway.
- No toco los datos que pertenecen a Identity, Order o Reporting.
