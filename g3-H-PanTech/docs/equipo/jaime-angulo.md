# Plan de trabajo — Jaime Darley Angulo Tenorio

## Rol

**Plataforma, integración y entrega.** Soy el dueño de la infraestructura, los contratos entre servicios, la integración end-to-end, la documentación arquitectónica y el PR final. También apoyo la implementación del API Gateway y resuelvo bloqueos de los demás.

| Campo | Valor |
|---|---|
| Rama personal | `g3-H/jaime-plataforma` |
| Rama de integración | `g3-H/Pantech-P2` |
| Componentes propios | `docker-compose.p2.yml`, `infrastructure/`, `scripts/`, `docs/`, CI |
| Lenguajes | YAML, SQL, Python (scripts), Go (apoyo al Gateway) |
| Depende de | Diego (JWT), Yeison (eventos y datos), Axel (Gateway/Kitchen) |
| Bloquea a | Todos: contratos, compose, bases de datos y broker |

## Misión

Que los cuatro componentes de cada persona se puedan levantar juntos, se comuniquen con los contratos correctos y se entreguen en un solo artefacto reproducible. Mi trabajo no es reemplazar a los dueños de cada servicio: es definir los acuerdos, preparar la plataforma y asegurar que la integración no se rompa.

## Responsabilidades

1. **Contratos compartidos:** OpenAPI, claims del JWT, esquemas de eventos, puertos y variables de entorno.
2. **Infraestructura:** `docker-compose.p2.yml` con los 12 contenedores, red, volúmenes, healthchecks y dependencias.
3. **Datos e integraciones:** inicialización de PostgreSQL, MongoDB, Redis, MinIO y RabbitMQ; buckets, colecciones, colas y datos semilla.
4. **Integración:** script de smoke test end-to-end, pruebas de denegación y guion de demostración.
5. **Documentación:** README, vistas C&C y despliegue, enlaces a los planes de equipo y matriz NFR.
6. **Entrega:** revisión de PRs, resolución de conflictos, rama de integración y PR final.

## Fases y tareas

### Fase 0 — Contratos y esqueleto (primera semana)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| J0.1 | Publicar el contrato de puertos y variables de entorno | Tabla en `docs/equipo/contratos.md` o sección del README | Cada servicio sabe su puerto, su URL interna y sus variables |
| J0.2 | Publicar el contrato del JWT | Documento con claims, algoritmo, JWKS y expiración | Identity, Gateway y servicios validan lo mismo |
| J0.3 | Publicar los esquemas de eventos | `order.paid`, `order.ready`, `reservation.created`, `reservation.cancelled` | Productores y consumidores usan el mismo nombre y payload |
| J0.4 | Crear el esqueleto de `docker-compose.p2.yml` | 12 servicios declarados, aunque algunos aún no tengan código | `docker compose config` valida sin errores |
| J0.5 | Crear `.env.p2.example` | Variables de todas las piezas | Nadie hardcodea contraseñas ni URLs |
| J0.6 | Crear la estructura de `infrastructure/` | Carpetas `postgres/`, `mongodb/`, `redis/`, `minio/`, `rabbitmq/` | Cada dueño sabe dónde poner su init |

### Fase 1 — Plataforma base (semanas 2–3)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| J1.1 | PostgreSQL: esquemas `auth`, `orders`, `kitchen`, `reporting` | `infrastructure/postgres/init.sql` | Los servicios conectan y crean sus tablas por migración |
| J1.2 | MongoDB: colecciones e índices | `menu`, `order_history`, `reservation_read`, `report_snapshots` | Índices por `customer_id`, `order_id` y fecha |
| J1.3 | Redis: configuración de persistencia | AOF para la cola; prefijos de llaves | La cola sobrevive a un reinicio del contenedor |
| J1.4 | MinIO: buckets | `receipts`, `products`, `reports` | Worker y Reporting pueden escribir; lectura pública solo de productos |
| J1.5 | RabbitMQ: exchange, colas, bindings y DLQ | `panaderia.events` (topic), colas de Worker/Reporting y `panaderia.dlq` | Un evento publicado llega a la cola correcta |
| J1.6 | Datos semilla | Roles, permisos, usuarios demo y productos | Se puede iniciar sesión con cada rol y hay menú para probar |
| J1.7 | Healthchecks y dependencias | `depends_on` con `condition` | `docker compose up` espera a las bases antes de los servicios |

### Fase 2 — Integración (semanas 4–5)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| J2.1 | Apoyar el API Gateway | Ruteo, validación JWT y propagación de claims | Gateway responde 401 sin token y enruta con token válido |
| J2.2 | Script de humo end-to-end | `scripts/smoke_test.py` | Registro → menú → pedido → pago → worker → cocina → listo → entrega → historial |
| J2.3 | Pruebas de denegación | Casos documentados y ejecutables | Cliente no ve pedidos ajenos; cocinero no cobra; mesero no cambia cocina; supervisor no gestiona roles |
| J2.4 | Web SSR y KDS conectados | Variables de entorno y CORS correctos | El navegador y el KDS usan el Gateway, nunca los servicios directos |
| J2.5 | Observabilidad mínima | Logs estructurados y request ID | Un pedido se puede seguir por los logs de Gateway, Order, Worker y Kitchen |
| J2.6 | Guion de demostración | `docs/equipo/demo.md` | La demo se puede repetir desde cero en menos de 10 minutos |

### Fase 3 — Cierre y entrega (semana 6)

| ID | Tarea | Entregable | Criterio de aceptación |
|---|---|---|---|
| J3.1 | Revisar y mergear PRs | Rama `g3-H/Pantech-P2` estable | Todos los PRs tienen revisión y checks verdes |
| J3.2 | Actualizar README y diagramas | Cuatro vistas y matriz NFR al día | Las URLs, puertos y componentes coinciden con el compose |
| J3.3 | Verificar instalación desde cero | Clonar, copiar `.env`, levantar | Funciona sin pasos ocultos |
| J3.4 | Verificar formato de entrega | Rama y carpeta según el PDF o según lo acordado con el profesor | El artefacto queda en el lugar correcto |
| J3.5 | Abrir el PR final | PR a `main` con resumen, capturas y checklist | Aprobado por el profesor o por el revisor del curso |

## Contratos que debo publicar primero

### Puertos

| Servicio | Puerto | URL interna |
|---|---:|---|
| `web-ssr` | 3000 | `http://web-ssr:3000` |
| `api-gateway` | 8080 | `http://api-gateway:8080` |
| `identity-service` | 8002 | `http://identity-service:8002` |
| `order-service` | 8000 | `http://order-service:8000` |
| `kitchen-service` | 8001 | `http://kitchen-service:8001` |
| `reporting-service` | 8003 | `http://reporting-service:8003` |
| `rabbitmq` | 5672 / 15672 | `amqp://rabbitmq:5672` |
| `postgres` | 5432 | `postgresql://postgres:5432` |
| `mongodb` | 27017 | `mongodb://mongodb:27017` |
| `redis` | 6379 | `redis://redis:6379` |
| `minio` | 9000 / 9001 | `http://minio:9000` |

### JWT

- Algoritmo: **RS256**.
- Emisor: `identity-service`.
- Claims: `sub`, `email`, `roles`, `permissions`, `level`, `iss`, `iat`, `exp`.
- Expiración: 15 minutos para el access token; refresh token de 7 días.
- Distribución: Identity expone `GET /.well-known/jwks.json`; Gateway y servicios cachean la clave pública.
- Servicio a servicio: `X-Service-Token` con un secreto compartido solo para el prototipo.

### Eventos

| Evento | Productor | Consumidor | Payload mínimo |
|---|---|---|---|
| `order.paid` | Order Service | Worker | `event_id`, `order_id`, `customer_id`, `total`, `paid_at` |
| `order.ready` | Kitchen Service | Worker, Reporting | `event_id`, `order_id`, `ready_at` |
| `reservation.created` | Order Service | Worker | `event_id`, `reservation_id`, `customer_id`, `date`, `time` |
| `reservation.cancelled` | Order Service | Worker | `event_id`, `reservation_id`, `customer_id`, `reason` |

Todos los eventos llevan `event_id` para idempotencia y `occurred_at` para auditoría.

## Integración y PR

### Regla de ramas

- Cada persona trabaja en su rama personal.
- Los PRs van a `g3-H/Pantech-P2`, no directo a `main`.
- Yo reviso, pido cambios y mergeo.
- El PR final va de `g3-H/Pantech-P2` a `main`.

### Checklist de revisión de PR

- [ ] El servicio compila y pasa sus pruebas.
- [ ] Tiene `Dockerfile` y healthcheck.
- [ ] Está declarado en `docker-compose.p2.yml` con sus variables.
- [ ] No tiene secretos hardcodeados.
- [ ] Actualiza su OpenAPI o su README.
- [ ] Incluye al menos un caso feliz y uno de denegación.
- [ ] No rompe los contratos de JWT, eventos ni puertos.
- [ ] Los diagramas o el README se actualizan si cambió una relación.

### Orden de merge recomendado

1. Contratos e infraestructura.
2. Identity Service.
3. API Gateway.
4. Order Service.
5. Kitchen Service.
6. Worker.
7. Reporting Service.
8. Web SSR.
9. KDS/POS.
10. Ajustes de integración y entrega.

## Documentación y diagramas

- Mantener `docs/Vista CyC P2.puml` y `docs/tiers_despliegue P2.puml` alineados con el compose real.
- Revisar que `docs/capas P2.puml` y `docs/descomposicion P2.puml` sigan mostrando los mismos componentes.
- Actualizar la matriz de cumplimiento NFR del README cuando un requisito pase de “diseño” a “implementado”.
- Mantener los cuatro planes de `docs/equipo/` enlazados desde el README.

## Definición de terminado

- `docker compose -f docker-compose.p2.yml up --build` levanta los 12 contenedores.
- Todos los healthchecks pasan.
- El smoke test termina en verde.
- Las pruebas de denegación pasan.
- El README permite a otra persona desplegar desde cero.
- El PR final está aprobado y la rama queda sincronizada.

## Riesgos y mitigación

| Riesgo | Mitigación |
|---|---|
| Cuello de botella en integración | Contratos primero y compose esqueleto desde la fase 0 |
| Servicios que no respetan puertos o variables | Tabla única de contratos y revisión de PR |
| PRs grandes y conflictivos | PRs pequeños por componente; integración semanal |
| Diferencias entre el PDF y el repo | Confirmar rama/carpeta con el profesor antes de la entrega |
| Falta de tiempo para los 12 contenedores | Priorizar el camino crítico: Identity → Gateway → Order → Kitchen → Worker → Web SSR; Reporting y KDS al final |

## Primer PR

**`g3-H/jaime-plataforma`:** contratos, `.env.p2.example`, esqueleto de `docker-compose.p2.yml` e `infrastructure/` con PostgreSQL, MongoDB, Redis, MinIO y RabbitMQ. Sin lógica de negocio.

## Lo que no hago

- No implemento la lógica interna de Identity, Order, Kitchen, Reporting, Web SSR o KDS.
- No cambio el alcance funcional sin acordarlo con el dueño del componente.
- No mergeo un PR sin pruebas y sin documentación.
