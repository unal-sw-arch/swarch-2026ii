# Tiempo real

`RestaurantCoordinator` acepta WebSockets, serializa mutaciones y publica parches versionados. `events.ts` traduce respuestas exitosas a cambios de recursos.

También actúa como el componente de procesamiento asíncrono del sistema: una
alarma del Durable Object drena la cola `async_jobs` (ver `features/async_jobs`)
para ejecutar efectos diferidos (escrituras de métricas) fuera de la ruta de la
petición, con reintentos y backoff.
