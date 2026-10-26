-- name: lock_release
-- version: 1
-- keys: KEYS[1] = lock:{recurso}
-- args: ARGV[1] = token
-- returns: 1 si liberó, 0 si el lock no pertenecía al token (compare-and-delete)
-- cluster-safe: sí
if redis.call("GET", KEYS[1]) == ARGV[1] then
  return redis.call("DEL", KEYS[1])
end
return 0
