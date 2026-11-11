-- name: ratelimit_sliding_window
-- version: 1
-- keys: KEYS[1] = rl:v1:{tipo-sujeto:id}:politica  (ZSET de timestamps de cada intento)
-- args: ARGV[1] = capacidad (maximo de intentos dentro de la ventana)
--       ARGV[2] = ventana en ms
--       ARGV[3] = tokens solicitados (normalmente 1)
--       ARGV[4] = id unico de esta llamada (evita colision de miembros en el mismo ms)
-- returns: { permitido (1/0), restantes (entero), retry_after en ms }
-- cluster-safe: si (una sola clave)
-- nota: ventana DESLIZANTE exacta (sin rafaga de borde). Usa el reloj de Redis (TIME).
local capacity  = tonumber(ARGV[1])
local window    = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local member    = ARGV[4]

local t   = redis.call("TIME")
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

-- Descartar los intentos que ya salieron de la ventana
redis.call("ZREMRANGEBYSCORE", KEYS[1], 0, now - window)
local count = redis.call("ZCARD", KEYS[1])

local allowed = 0
local retry_after = 0
if count + requested <= capacity then
  for i = 1, requested do
    redis.call("ZADD", KEYS[1], now, member .. ":" .. i)
  end
  allowed = 1
  count = count + requested
else
  -- Cuanto falta para que el intento mas antiguo caiga fuera de la ventana
  local oldest = redis.call("ZRANGE", KEYS[1], 0, 0, "WITHSCORES")
  if oldest[2] ~= nil then
    retry_after = (tonumber(oldest[2]) + window) - now
    if retry_after < 0 then retry_after = 0 end
  else
    retry_after = window
  end
end

-- La clave se autolimpia una ventana despues del ultimo intento
redis.call("PEXPIRE", KEYS[1], window)

local remaining = capacity - count
if remaining < 0 then remaining = 0 end
return { allowed, remaining, retry_after }
