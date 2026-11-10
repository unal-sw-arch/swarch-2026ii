# ADR-0015: Difusión por canal de usuario y presencia por `PUBSUB NUMSUB`

- **Estado:** Aceptado — concreta ADR-005 y §11 de la guía
- **Fecha:** 2026-11-09

## Contexto
Con varias instancias, el destinatario puede estar conectado a otra. La guía menciona canales `chat:conv:{id}` y
`chat:user:{id}` en el Redis Pub/Sub propio. Además el chat necesita saber si el destinatario está conectado para
decidir si notifica (ADR-011), y la presencia con TTL es de la fase 2.

## Decisión
- Solo canales por usuario: `chat:user:{id}`. Cada instancia se suscribe al canal de cada usuario que tiene
  conectado (primera sesión) y se desuscribe al cerrar la última. Para entregar, se publica en el canal de cada
  participante; el mensaje es el frame JSON ya listo para el cliente.
- «En línea» = `PUBSUB NUMSUB chat:user:{id}` > 0 en el Redis Pub/Sub propio. No se guardan claves de presencia.

## Alternativas consideradas
- **Canales por conversación:** cada instancia tendría que suscribirse a todas las conversaciones de sus usuarios
  (muchas más suscripciones) y no resuelve «¿está en línea?».
- **Claves de presencia con TTL:** es el diseño de la fase 2 (instancia de caché, que aún no existe).

## Consecuencias
- Sin sesiones pegajosas. Si el Pub/Sub cae, los mensajes se guardan igual y se recuperan con `sync`/REST; mientras
  tanto `NUMSUB` falla y el chat notifica «por si acaso».
- El emisor también recibe `message.new` en sus otras sesiones (y en la misma): el cliente deduplica por `message_id`.
- `NUMSUB` exige la API nativa de Lettuce: el `execute` genérico de Spring Data no lee respuestas enteras (lo
  cubre la prueba de punta a punta).
