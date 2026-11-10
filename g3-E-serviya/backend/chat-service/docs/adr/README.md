# ADRs del chat-service

Las decisiones de base están en la guía (`documents/microservicios/chat/guia-microservicio-chat.md` §13, ADR-001 a
ADR-013) y siguen vigentes **salvo donde un ADR de esta carpeta las reemplaza**. Aquí solo se registran las
decisiones tomadas al implementar, que cambian o concretan la guía. Las de infraestructura compartida (red,
usuarios, topología, envelope, puertos) son de
[`decisiones-infraestructura-implementacion.md`](../../../../documents/microservicios/chat/decisiones-infraestructura-implementacion.md).

| ADR | Título | Estado |
|---|---|---|
| [0002](0002-java-spring-en-vez-de-python.md) | Java 21 + Spring Boot en vez de Python + FastAPI | Aceptado — reemplaza ADR-002 de la guía |
| [0014](0014-ruta-publica-api-chat-v1.md) | Ruta pública `/api/chat/v1/**` (contrato del gateway) | Aceptado |
| [0015](0015-difusion-por-canal-de-usuario.md) | Difusión por canal de usuario y presencia por `PUBSUB NUMSUB` | Aceptado |
| [0016](0016-comando-de-notificacion-v1.md) | Comando de notificación con el contrato v1 existente | Aceptado |
| [0017](0017-transacciones-y-outbox.md) | Unidad de trabajo READ COMMITTED y outbox marcado | Aceptado |
