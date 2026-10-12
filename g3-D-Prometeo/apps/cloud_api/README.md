# Cloud API

Único backend del sistema, implementado con Hono sobre Cloudflare Workers.

- `features`: rutas y reglas agrupadas por capacidad.
- `domain`: reglas que pertenecen al servidor cloud.
- `infrastructure`: integración con Durable Objects y tiempo real.
- `shared`: utilidades HTTP y D1 realmente transversales.
- `test`: pruebas del Worker y de reglas puras.
- `index.ts`: composición pública, autenticación de mutaciones y WebSocket.
- `mutationApp.ts`: router interno ejecutado por el Durable Object.
- `types.ts`: bindings y tipos del entorno Cloudflare.
