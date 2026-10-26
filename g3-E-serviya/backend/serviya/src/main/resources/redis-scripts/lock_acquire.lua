-- name: lock_acquire
-- version: 1
-- keys: KEYS[1] = lock:{recurso}
-- args: ARGV[1] = token (UUID por adquisición), ARGV[2] = ttl en milisegundos
-- returns: 1 si adquirió, 0 si está ocupado
-- cluster-safe: sí (una sola clave)
-- Variante SIN fencing (ADR-003): para secciones críticas idempotentes (p. ej. los @Scheduled).
-- Para fencing (recursos por-entidad, p. ej. deuda B8) usar lock_acquire_fenced.lua.
if redis.call("SET", KEYS[1], ARGV[1], "NX", "PX", ARGV[2]) then
  return 1
end
return 0
