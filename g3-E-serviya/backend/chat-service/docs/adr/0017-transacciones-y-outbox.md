# ADR-0017: Unidad de trabajo READ COMMITTED y outbox marcado

- **Estado:** Aceptado
- **Fecha:** 2026-11-09

## Contexto
El envío bloquea la conversación (`SELECT ... FOR UPDATE`) y luego busca un mensaje previo con el mismo
`client_message_id`. Con REPEATABLE READ (por defecto en InnoDB) esa lectura depende de cuándo se tomó la
instantánea. Por otro lado, el relay del monolito borra las filas del outbox al publicarlas.

## Decisión
- La unidad de trabajo usa READ COMMITTED: después del bloqueo, toda lectura ve lo que confirmó el envío anterior.
- El relay del chat marca `published_at` en vez de borrar, para poder auditar y reintentar. Reclama lotes con
  `FOR UPDATE SKIP LOCKED` (varias instancias sin duplicar) y marca solo tras la confirmación del broker.
- La conversación se actualiza con un puntero de lectura que solo avanza (`GREATEST`), para que una copia vieja no
  pise un «marcar leído» concurrente.
- La violación del índice único al abrir una conversación deja la transacción marcada para rollback: el caso de uso
  relee en una unidad de trabajo nueva.

## Consecuencias
- La concurrencia está probada contra MySQL real (`ConcurrencyIntegrationTest`: 30 envíos simultáneos → seq 1..30;
  sin el bloqueo, 3 de 4 pruebas fallan).
- **Pendiente:** un job de limpieza de `outbox_events` publicados (p. ej. > 7 días); hoy la tabla solo crece.
