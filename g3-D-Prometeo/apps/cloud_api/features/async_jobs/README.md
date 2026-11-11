# Trabajos asíncronos

Componente responsable del procesamiento asíncrono del sistema. Desacopla los
efectos secundarios que antes corrían de forma síncrona en la ruta de mutación
(hoy: las escrituras al gateway de métricas) para que un downstream lento o
caído nunca bloquee ni haga fallar la mutación del usuario.

- `processor.ts`: núcleo puro. Decide reintento/backoff exponencial o descarte
  (`dead`) y ejecuta cada job según su `kind`. No conoce D1 ni el Durable Object.
- `repository.ts`: cola durable en D1 (tabla `async_jobs`): encolar (idempotente
  por `UNIQUE (kind, dedupe_key)`), reclamar jobs vencidos, marcar éxito/fallo y
  consultar el próximo vencimiento.
- `drain.ts`: `drainDueJobs` enlaza repositorio + procesador en un pase del loop
  y devuelve cuándo reprogramar la siguiente ejecución.

## Cómo encaja

1. Las rutas encolan el trabajo con `enqueueJob` tras confirmar su escritura en D1.
2. El `RestaurantCoordinator` (Durable Object) programa una alarma
   (`state.storage.setAlarm`). Su handler `alarm()` llama a `drainDueJobs`,
   reintenta los fallos con backoff y se reprograma mientras queden pendientes.
3. D1 sigue siendo la fuente de verdad; `dedupe_key` (la `idempotency-key` del
   coordinador) hace que los reintentos no dupliquen el efecto.
