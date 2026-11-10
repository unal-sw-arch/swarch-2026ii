# ADR-0016: Comando de notificación con el contrato v1 existente

- **Estado:** Aceptado — concreta ADR-011 de la guía
- **Fecha:** 2026-11-09

## Contexto
ADR-011: el chat decide y compone la notificación; Notificaciones solo entrega. El contrato que ya consume
`notifications-service` es `notification.requested.v1` (exchange `serviya.notifications`), con formato propio
(camelCase, `schemaVersion`) distinto del sobre común de la guía, y un campo `sealed` cifrado con una clave
compartida para correo y datos sensibles.

## Decisión
El chat emite exactamente ese formato v1: `type = chat_message`, `title = "Nuevo mensaje"`,
`message = "Tienes un mensaje nuevo de {nombre}"`, `entityType = CONVERSATION`, `entityId = conversation_id`,
`channels = ["INTERNAL"]` y **sin `sealed`**. Sale por el mismo outbox del chat, en la transacción del mensaje.

## Consecuencias
- No hace falta tocar `notifications-service` ni compartir `NOTIFICATIONS_PAYLOAD_KEY` con el chat.
- Sin `sealed` no se puede pedir el canal EMAIL (el correo viaja sellado). Si se quiere correo, el chat necesitaría la
  clave de sellado y el correo del destinatario, que hoy no tiene (minimización de datos): decidirlo aparte.
- El aviso nunca lleva el contenido del mensaje (DP-04). No hay agrupación: con el destinatario desconectado, cada
  mensaje genera un aviso.
