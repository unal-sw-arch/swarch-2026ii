# redis-scripts — scripts Lua compartidos

**Único origen** de los algoritmos atómicos para todos los lenguajes (regla §3.4 de la guía).

- Nombre: `<dominio>_<accion>.lua` (p. ej. `lock_release.lua`).
- Encabezado obligatorio en cada script: `name`, `version`, `keys`, `args`, `returns`, `cluster-safe`.
- Todas las claves se pasan en `KEYS` (nunca se construyen dentro del script) y comparten **hash tag**.
- Un cambio incompatible crea un script nuevo (`version` + 1), nunca se modifica en caliente.
- Ejecución con `EVALSHA` (en Spring: `DefaultRedisScript`, que maneja `SCRIPT LOAD`/`NOSCRIPT`).

## Ubicación real de los scripts

Mientras el backend sea el único consumidor (monolito Java), los `.lua` **canónicos** viven en el
**classpath del backend**: `backend/serviya/src/main/resources/redis-scripts/`. Motivo: el contexto de
build de la imagen Docker es `./backend/serviya`, así que nada fuera de ahí entra en la imagen, y se cargan
con `ClassPathResource`. Este directorio raíz queda como **spec** (este README) y como **origen compartido**
cuando aparezcan servicios en otros lenguajes (Fase 9), momento en que se definirá cómo distribuirlos.

## Scripts

- `lock_acquire.lua` (sin fencing), `lock_acquire_fenced.lua` (con fencing), `lock_release.lua`,
  `lock_renew.lua` — **Fase 2 ✅** (deuda #1, lock de `@Scheduled`). Ver `documents/redis/protocolo-lock.md`.
- `ratelimit_token_bucket.lua` — **Fase 3** (rate limiting distribuido, #6 / M13).
