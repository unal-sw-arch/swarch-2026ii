# infra/redis — infraestructura de Redis (ServiYa)

Sustrato de coordinación. Fuente de verdad: `documents/redis/guia-redis-implementacion.md`.
Adopción en modalidad **Vía B**: hoy solo `redis-coord` (locks); `redis-cache` se añade en la **Fase 6**.

## Contenido

- `.env.example` — nombres de variables (sin valores). Copiar a `.env` (no versionado) y rellenar.
- `conf/` — `coord.conf` (y `cache.conf` en Fase 6). **Se crean en la Fase 1.**
- `acl/` — `users.acl.tpl` (plantilla versionada) + `render-acl.sh`. **Se crean en la Fase 1.**
  El `users.acl` **generado** NO se versiona (contiene hashes).
- `docker-compose.redis.yml` — **se crea en la Fase 1.**

## Uso (a partir de la Fase 1)

Redis **no** expone puertos fuera de la red interna de Docker. Acceso de depuración local:
`docker compose exec redis-coord redis-cli --user admin`.
