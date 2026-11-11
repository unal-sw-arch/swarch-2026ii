package tests

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"sync"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"

	"kitchen-service/internal/events"
)

type fakePublisher struct {
	mu       sync.Mutex
	failures int
	sent     []published
}

type published struct {
	routingKey string
	messageID  string
	body       []byte
}

func (f *fakePublisher) Publish(_ context.Context, routingKey, messageID string, body []byte) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failures > 0 {
		f.failures--
		return errors.New("rabbitmq caído")
	}
	f.sent = append(f.sent, published{routingKey, messageID, body})
	return nil
}

func newOutbox(t *testing.T, pub events.Publisher) *events.Outbox {
	t.Helper()
	mr := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: mr.Addr()})
	t.Cleanup(func() { rdb.Close() })
	return events.NewOutbox(rdb, pub, slog.New(slog.NewJSONHandler(io.Discard, nil)))
}

func TestOrderReadyEnvelope(t *testing.T) {
	pub := &fakePublisher{}
	ob := newOutbox(t, pub)
	readyAt := time.Date(2026, 11, 9, 14, 45, 0, 0, time.UTC)

	if err := ob.OrderReady(t.Context(), orderA, customer, readyAt, 12); err != nil {
		t.Fatal(err)
	}
	if n, _ := ob.Flush(t.Context()); n != 1 || len(pub.sent) != 1 {
		t.Fatalf("debe publicarse un evento, salieron %d", len(pub.sent))
	}

	msg := pub.sent[0]
	var env struct {
		EventID    string         `json:"event_id"`
		EventType  string         `json:"event_type"`
		Version    int            `json:"version"`
		OccurredAt time.Time      `json:"occurred_at"`
		Producer   string         `json:"producer"`
		Payload    map[string]any `json:"payload"`
	}
	if err := json.Unmarshal(msg.body, &env); err != nil {
		t.Fatal(err)
	}
	if msg.routingKey != "order.ready" || env.EventType != "order.ready" || env.Version != 1 || env.Producer != "kitchen-service" {
		t.Fatalf("sobre: %+v (routing %s)", env, msg.routingKey)
	}
	if env.EventID == "" || env.EventID != msg.messageID || env.OccurredAt.IsZero() {
		t.Fatalf("event_id/occurred_at: %+v", env)
	}
	want := map[string]any{"order_id": orderA, "customer_id": customer, "ready_at": "2026-11-09T14:45:00Z", "prep_minutes": float64(12)}
	if len(env.Payload) != len(want) {
		t.Fatalf("payload = %v", env.Payload)
	}
	for k, v := range want {
		if env.Payload[k] != v {
			t.Fatalf("payload[%s] = %v, esperado %v", k, env.Payload[k], v)
		}
	}
}

func TestOutboxRetriesAndKeepsTheSameEventID(t *testing.T) {
	pub := &fakePublisher{failures: 2}
	ob := newOutbox(t, pub)
	if err := ob.OrderReady(t.Context(), orderA, customer, t0, 5); err != nil {
		t.Fatal(err)
	}

	for i := 1; i <= 2; i++ {
		if n, failed := ob.Flush(t.Context()); n != 0 || failed != 1 {
			t.Fatalf("intento %d: published=%d failed=%d", i, n, failed)
		}
		if pending, _ := ob.Pending(t.Context()); pending != 1 {
			t.Fatalf("el evento no debe perderse mientras RabbitMQ falla (pendientes=%d)", pending)
		}
	}
	if n, failed := ob.Flush(t.Context()); n != 1 || failed != 0 {
		t.Fatalf("tercer intento: published=%d failed=%d", n, failed)
	}
	if pending, _ := ob.Pending(t.Context()); pending != 0 {
		t.Fatalf("pendientes = %d", pending)
	}
	if n, _ := ob.Flush(t.Context()); n != 0 || len(pub.sent) != 1 {
		t.Fatal("un evento confirmado no se vuelve a publicar")
	}
}

func TestOutboxWorkerPublishesInBackground(t *testing.T) {
	pub := &fakePublisher{failures: 1}
	ob := newOutbox(t, pub)
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	go ob.Run(ctx)

	if err := ob.OrderReady(ctx, orderA, customer, t0, 5); err != nil {
		t.Fatal(err)
	}
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		pub.mu.Lock()
		n := len(pub.sent)
		pub.mu.Unlock()
		if n == 1 {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatal("el worker no reintentó y publicó el evento a tiempo")
}
