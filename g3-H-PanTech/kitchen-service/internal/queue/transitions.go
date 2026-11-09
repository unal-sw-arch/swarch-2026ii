package queue

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

const inPrepKey = "kitchen:in_preparation"

// Atómico. Devuelve {código, estado, en preparación}: 0 no existe, 1 estado inválido, 2 sin capacidad, 3 hecho.
var transitionScript = redis.NewScript(`
local status = redis.call('HGET', KEYS[1], 'status')
if not status then return {0, '', 0} end
local inprep = redis.call('SCARD', KEYS[3])
if status ~= ARGV[2] then return {1, status, inprep} end
local cap = tonumber(ARGV[4])
if cap >= 0 and inprep >= cap then return {2, status, inprep} end
redis.call('HSET', KEYS[1], 'status', ARGV[3], ARGV[6], ARGV[5])
if ARGV[3] == 'en_preparacion' then
  redis.call('SADD', KEYS[3], ARGV[1])
elseif ARGV[2] == 'en_preparacion' then
  redis.call('SREM', KEYS[3], ARGV[1])
end
if ARGV[3] == 'entregado' then
  redis.call('ZREM', KEYS[2], ARGV[1])
  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]))
end
return {3, status, inprep}
`)

func (s *Store) Transition(ctx context.Context, id, from, to string, capacity int, at time.Time, ttl time.Duration) error {
	field := map[string]string{StatusEnPreparacion: "started_at", StatusListo: "ready_at", StatusEntregado: "delivered_at"}[to]
	res, err := transitionScript.Run(ctx, s.rdb, []string{orderKey(id), queueKey, inPrepKey},
		id, from, to, capacity, formatTime(at), field, int(ttl.Seconds())).Slice()
	if err != nil {
		return fmt.Errorf("cambiar estado en redis: %w", err)
	}
	code, _ := res[0].(int64)
	current, _ := res[1].(string)
	inPrep, _ := res[2].(int64)
	switch code {
	case 0:
		return ErrNotFound
	case 1:
		return &InvalidStateError{Current: current, Requested: to}
	case 2:
		return &CapacityError{Max: capacity, InPreparation: int(inPrep)}
	}
	return nil
}

func (s *Store) InPreparation(ctx context.Context) (int, error) {
	n, err := s.rdb.SCard(ctx, inPrepKey).Result()
	return int(n), err
}
