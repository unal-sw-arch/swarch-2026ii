-- name: lock_acquire_fenced
-- version: 1
-- keys: KEYS[1] = lock:{recurso}, KEYS[2] = fence:{recurso}
-- args: ARGV[1] = token (UUID por adquisición), ARGV[2] = ttl en milisegundos
-- returns: fencing token (>0) si adquirió, 0 si está ocupado
-- cluster-safe: sí (ambas claves comparten hash tag {recurso})
-- Variante CON fencing (D-05): para secciones críticas por-entidad que validan un token
-- estrictamente creciente en la escritura (p. ej. deuda B8). NO la usan los @Scheduled (ADR-003).
if redis.call("SET", KEYS[1], ARGV[1], "NX", "PX", ARGV[2]) then
  return redis.call("INCR", KEYS[2])
end
return 0
