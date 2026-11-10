-- name: ratelimit_token_bucket
-- version: 1
-- keys: KEYS[1] = rl:v1:{tipo-sujeto:id}:politica  (HASH con campos tokens, ts)
-- args: ARGV[1] = capacidad (maximo de rafaga)
--       ARGV[2] = tokens repuestos por segundo (tasa sostenida)
--       ARGV[3] = tokens solicitados
-- returns: { permitido (1/0), tokens restantes (entero), retry_after en ms }
-- cluster-safe: si (una sola clave)
-- nota: usa el reloj de Redis (TIME), no el de los clientes
local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local t   = redis.call("TIME")
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local data   = redis.call("HMGET", KEYS[1], "tokens", "ts")
local tokens = tonumber(data[1])
local ts     = tonumber(data[2])
if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsed * rate / 1000))

local allowed = 0
local retry_after = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry_after = math.ceil((requested - tokens) * 1000 / rate)
end

redis.call("HSET", KEYS[1], "tokens", tostring(tokens), "ts", now)
-- TTL: tiempo de rellenar el bucket completo x2; tras inactividad la clave se borra sola
redis.call("PEXPIRE", KEYS[1], math.ceil(capacity * 1000 / rate) * 2)

return { allowed, math.floor(tokens), retry_after }
