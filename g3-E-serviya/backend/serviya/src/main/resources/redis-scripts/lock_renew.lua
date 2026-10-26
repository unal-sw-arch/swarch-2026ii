-- name: lock_renew
-- version: 1
-- keys: KEYS[1] = lock:{recurso}
-- args: ARGV[1] = token, ARGV[2] = nuevo ttl en milisegundos
-- returns: 1 si renovó, 0 si el lock ya no pertenece al token (se perdió)
-- cluster-safe: sí
if redis.call("GET", KEYS[1]) == ARGV[1] then
  return redis.call("PEXPIRE", KEYS[1], ARGV[2])
end
return 0
