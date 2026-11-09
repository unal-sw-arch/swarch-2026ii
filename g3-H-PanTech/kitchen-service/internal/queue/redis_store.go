package queue

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// kitchen:queue (ZSET, score = priority_score) y kitchen:order:<id> (HASH).
// Al entregarse, la comanda sale del ZSET y el hash conserva un TTL.
const (
	queueKey    = "kitchen:queue"
	orderPrefix = "kitchen:order:"
)

const DeliveredTTL = 24 * time.Hour

// enqueueScript es atómico: no pisa una comanda existente.
var enqueueScript = redis.NewScript(`
if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end
redis.call('HSET', KEYS[1], unpack(ARGV, 3))
redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
return 1
`)

type Store struct {
	rdb redis.Cmdable
}

func NewStore(rdb redis.Cmdable) *Store {
	return &Store{rdb: rdb}
}

func orderKey(id string) string { return orderPrefix + id }

func formatTime(t time.Time) string { return t.UTC().Format(time.RFC3339Nano) }

func parseTime(raw string) (*time.Time, error) {
	if raw == "" {
		return nil, nil
	}
	t, err := time.Parse(time.RFC3339Nano, raw)
	if err != nil {
		return nil, err
	}
	return &t, nil
}

func encode(o Order) ([]interface{}, error) {
	items, err := json.Marshal(o.Items)
	if err != nil {
		return nil, err
	}
	fields := []interface{}{
		"order_id", o.OrderID,
		"customer_id", o.CustomerID,
		"order_type", o.OrderType,
		"status", o.Status,
		"prep_minutes", strconv.Itoa(o.PrepMinutes),
		"paid_at", formatTime(o.PaidAt),
		"enqueued_at", formatTime(o.EnqueuedAt),
		"items", string(items),
	}
	if o.StartedAt != nil {
		fields = append(fields, "started_at", formatTime(*o.StartedAt))
	}
	if o.ReadyAt != nil {
		fields = append(fields, "ready_at", formatTime(*o.ReadyAt))
	}
	return fields, nil
}

func decode(data map[string]string) (Order, error) {
	var o Order
	prep, err := strconv.Atoi(data["prep_minutes"])
	if err != nil {
		return o, fmt.Errorf("prep_minutes inválido: %w", err)
	}
	paidAt, err := parseTime(data["paid_at"])
	if err != nil || paidAt == nil {
		return o, fmt.Errorf("paid_at inválido: %v", err)
	}
	enqueuedAt, err := parseTime(data["enqueued_at"])
	if err != nil || enqueuedAt == nil {
		return o, fmt.Errorf("enqueued_at inválido: %v", err)
	}
	startedAt, err := parseTime(data["started_at"])
	if err != nil {
		return o, fmt.Errorf("started_at inválido: %w", err)
	}
	readyAt, err := parseTime(data["ready_at"])
	if err != nil {
		return o, fmt.Errorf("ready_at inválido: %w", err)
	}
	var items []OrderItem
	if raw := data["items"]; raw != "" {
		if err := json.Unmarshal([]byte(raw), &items); err != nil {
			return o, fmt.Errorf("items inválido: %w", err)
		}
	}
	return Order{
		OrderID:     data["order_id"],
		CustomerID:  data["customer_id"],
		OrderType:   data["order_type"],
		Status:      data["status"],
		PrepMinutes: prep,
		PaidAt:      *paidAt,
		EnqueuedAt:  *enqueuedAt,
		StartedAt:   startedAt,
		ReadyAt:     readyAt,
		Items:       items,
	}, nil
}

// Enqueue es idempotente: created=false si el order_id ya existía.
func (s *Store) Enqueue(ctx context.Context, o Order, score float64) (bool, error) {
	fields, err := encode(o)
	if err != nil {
		return false, err
	}
	args := append([]interface{}{strconv.FormatFloat(score, 'f', -1, 64), o.OrderID}, fields...)
	res, err := enqueueScript.Run(ctx, s.rdb, []string{orderKey(o.OrderID), queueKey}, args...).Int()
	if err != nil {
		return false, fmt.Errorf("encolar en redis: %w", err)
	}
	return res == 1, nil
}

func (s *Store) Get(ctx context.Context, id string) (*Order, error) {
	data, err := s.rdb.HGetAll(ctx, orderKey(id)).Result()
	if err != nil {
		return nil, fmt.Errorf("leer comanda: %w", err)
	}
	if len(data) == 0 {
		return nil, nil
	}
	o, err := decode(data)
	if err != nil {
		return nil, fmt.Errorf("comanda %s corrupta: %w", id, err)
	}
	return &o, nil
}

// ListActive limpia del ZSET las referencias cuyo hash expiró.
func (s *Store) ListActive(ctx context.Context) ([]Order, error) {
	ids, err := s.rdb.ZRange(ctx, queueKey, 0, -1).Result()
	if err != nil {
		return nil, fmt.Errorf("leer cola: %w", err)
	}
	if len(ids) == 0 {
		return nil, nil
	}

	pipe := s.rdb.Pipeline()
	cmds := make([]*redis.MapStringStringCmd, len(ids))
	for i, id := range ids {
		cmds[i] = pipe.HGetAll(ctx, orderKey(id))
	}
	if _, err := pipe.Exec(ctx); err != nil && !errors.Is(err, redis.Nil) {
		return nil, fmt.Errorf("leer comandas: %w", err)
	}

	orders := make([]Order, 0, len(ids))
	var stale []interface{}
	for i, cmd := range cmds {
		data, err := cmd.Result()
		if err != nil {
			return nil, fmt.Errorf("leer comanda %s: %w", ids[i], err)
		}
		if len(data) == 0 {
			stale = append(stale, ids[i])
			continue
		}
		o, err := decode(data)
		if err != nil {
			return nil, fmt.Errorf("comanda %s corrupta: %w", ids[i], err)
		}
		orders = append(orders, o)
	}
	if len(stale) > 0 {
		if err := s.rdb.ZRem(ctx, queueKey, stale...).Err(); err != nil {
			return nil, fmt.Errorf("limpiar cola: %w", err)
		}
	}
	return orders, nil
}

func (s *Store) UpdateScores(ctx context.Context, scores map[string]float64) error {
	if len(scores) == 0 {
		return nil
	}
	members := make([]redis.Z, 0, len(scores))
	for id, score := range scores {
		members = append(members, redis.Z{Score: score, Member: id})
	}
	if err := s.rdb.ZAddXX(ctx, queueKey, members...).Err(); err != nil {
		return fmt.Errorf("actualizar scores: %w", err)
	}
	return nil
}

func (s *Store) Ping(ctx context.Context) error {
	return s.rdb.Ping(ctx).Err()
}
