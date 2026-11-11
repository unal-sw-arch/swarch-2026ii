package tests

import (
	"context"
	"errors"
	"sync"
	"time"
)

type historyRow struct {
	OrderID   string
	Status    string
	ChangedBy *string
	At        time.Time
}

type fakeHistory struct {
	mu   sync.Mutex
	rows []historyRow
	fail bool
}

func (f *fakeHistory) Record(_ context.Context, orderID, status string, changedBy *string, at time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.fail {
		return errors.New("postgres caído")
	}
	f.rows = append(f.rows, historyRow{orderID, status, changedBy, at})
	return nil
}

func (f *fakeHistory) statuses(orderID string) []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	var out []string
	for _, r := range f.rows {
		if r.OrderID == orderID {
			out = append(out, r.Status)
		}
	}
	return out
}

type readyEvent struct {
	OrderID     string
	CustomerID  string
	ReadyAt     time.Time
	PrepMinutes int
}

type fakeSink struct {
	mu     sync.Mutex
	events []readyEvent
}

func (f *fakeSink) OrderReady(_ context.Context, orderID, customerID string, readyAt time.Time, prep int) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.events = append(f.events, readyEvent{orderID, customerID, readyAt, prep})
	return nil
}

func (f *fakeSink) count() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.events)
}
