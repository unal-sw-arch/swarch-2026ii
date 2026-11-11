# infra/redis — infraestructura de Redis (ServiYa)

Sustrato de coordinación. Fuente de verdad: `documents/redis/guia-redis-implementacion.md`.
Adopción en modalidad **Vía B**: hoy solo `redis-coord` (locks); `redis-cache` se añade en la **Fase 6**.

## Contenido

- `.env.example` — nombres de variables (sin valores). Copiar a `.env` (no versionado) y rellenar.
- `conf/` — `coord.conf` (y `cache.conf` en Fase 6). **Se crean en la Fase 1.**
- `acl/` — `users.coord.acl.tpl` (plantilla versionada) + `render-acl.sh`. El `users.coord.acl`
  **generado** NO se versiona (contiene hashes). Incluye el usuario `chat` para rate limiting y
  tickets WebSocket del chat, restringido a `~chat:*`.
- `docker-compose.redis.yml` — define `redis-coord` y el profile de notificaciones.

## Uso

```bash
set -a
. ./.env
set +a
(cd infra/redis/acl && sh ./render-acl.sh users.coord.acl.tpl users.coord.acl)
docker compose -f docker-compose.yml -f infra/redis/docker-compose.redis.yml up -d redis-coord
```

Requiere `REDIS_CHAT_PASSWORD` en `.env` además de las contraseñas ACL existentes.

Redis **no** expone puertos fuera de la red interna de Docker. Acceso de depuración local:
`docker compose exec redis-coord redis-cli --user admin`.
