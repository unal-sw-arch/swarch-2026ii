# ADR-0014: Ruta pública `/api/chat/v1/**` (contrato del gateway)

- **Estado:** Aceptado
- **Fecha:** 2026-11-09

## Contexto
La primera versión del chat expuso `/api/v1/chat/**` (convención `/api/v1/<recurso>` del resto de servicios). En
paralelo, infraestructura cerró el contrato del gateway en
`documents/microservicios/chat/decisiones-infraestructura-implementacion.md`: REST `/api/chat/**` y WebSocket
`/api/chat/v1/ws` hacia `chat-service:8000`, que es también el prefijo de la guía (§8.1).

## Decisión
Se adopta el contrato de infraestructura: REST en `/api/chat/v1/...`, WebSocket en `/api/chat/v1/ws`, puerto
interno 8000. El gateway tiene una ruta WebSocket propia (`CHAT_WS_URI`, `ws://`) que preserva `Origin` (el chat lo
valida en el handshake) y otra REST (`CHAT_SERVICE_URI`).

## Consecuencias
- Una v2 incompatible sería `/api/chat/v2/...`.
- Lo verifican `ApiGatewayRoutingTest` y `ApiGatewayWebSocketTest` (upgrade, query con el ticket y `Origin` llegan al chat).
