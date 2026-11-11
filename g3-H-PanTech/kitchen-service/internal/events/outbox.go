package events

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

const (
	outboxKey       = "kitchen:outbox"
	EventOrderReady = "order.ready"
	producer        = "kitchen-service"
)

type Envelope struct {
	EventID    string         `json:"event_id"`
	EventType  string         `json:"event_type"`
	Version    int            `json:"version"`
	OccurredAt time.Time      `json:"occurred_at"`
	Producer   string         `json:"producer"`
	Payload    map[string]any `json:"payload"`
}

// Guarda el evento en Redis hasta que RabbitMQ lo confirma; si falla no se revierte el estado.
type Outbox struct {
	rdb  redis.Cmdable
	pub  Publisher
	log  *slog.Logger
	now  func() time.Time
	kick chan struct{}
}

func NewOutbox(rdb redis.Cmdable, pub Publisher, log *slog.Logger) *Outbox {
	return &Outbox{rdb: rdb, pub: pub, log: log, now: time.Now, kick: make(chan struct{}, 1)}
}

func (o *Outbox) OrderReady(ctx context.Context, orderID, customerID string, readyAt time.Time, prepMinutes int) error {
	body, err := json.Marshal(Envelope{
		EventID:    uuid.NewString(),
		EventType:  EventOrderReady,
		Version:    1,
		OccurredAt: o.now().UTC(),
		Producer:   producer,
		Payload: map[string]any{
			"order_id":     orderID,
			"customer_id":  customerID,
			"ready_at":     readyAt.UTC().Format(time.RFC3339),
			"prep_minutes": prepMinutes,
		},
	})
	if err != nil {
		return err
	}
	var env Envelope
	_ = json.Unmarshal(body, &env)
	if err := o.rdb.HSet(ctx, outboxKey, env.EventID, string(body)).Err(); err != nil {
		return err
	}
	select {
	case o.kick <- struct{}{}:
	default:
	}
	return nil
}

func (o *Outbox) Pending(ctx context.Context) (int64, error) {
	return o.rdb.HLen(ctx, outboxKey).Result()
}

func (o *Outbox) Flush(ctx context.Context) (published, failed int) {
	entries, err := o.rdb.HGetAll(ctx, outboxKey).Result()
	if err != nil {
		o.log.Error("outbox: leer pendientes", "error", err.Error())
		return 0, 0
	}
	for id, raw := range entries {
		var env Envelope
		if err := json.Unmarshal([]byte(raw), &env); err != nil {
			o.log.Error("outbox: evento corrupto, se descarta", "event_id", id, "error", err.Error())
			o.rdb.HDel(ctx, outboxKey, id)
			continue
		}
		pubCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
		err := o.pub.Publish(pubCtx, env.EventType, env.EventID, []byte(raw))
		cancel()
		if err != nil {
			failed++
			o.log.Error("outbox: no se pudo publicar, se reintentará",
				"event_id", id, "event_type", env.EventType, "error", err.Error())
			continue
		}
		if err := o.rdb.HDel(ctx, outboxKey, id).Err(); err != nil {
			o.log.Error("outbox: no se pudo borrar el evento publicado", "event_id", id, "error", err.Error())
		}
		published++
	}
	return published, failed
}

// Reintenta con backoff de 1 s a 30 s.
// TODO verificar con el Worker que deduplique por event_id.
func (o *Outbox) Run(ctx context.Context) {
	backoff := time.Second
	for {
		_, failed := o.Flush(ctx)
		wait := 5 * time.Second
		if failed > 0 {
			wait = backoff
			if backoff *= 2; backoff > 30*time.Second {
				backoff = 30 * time.Second
			}
		} else {
			backoff = time.Second
		}
		select {
		case <-ctx.Done():
			return
		case <-o.kick:
		case <-time.After(wait):
		}
	}
}
