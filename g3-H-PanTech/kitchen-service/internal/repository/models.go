package repository

import "time"

// StatusEvent es una fila de kitchen.order_status_events.
type StatusEvent struct {
	OrderID   string
	Status    string
	ChangedAt time.Time
	ChangedBy *string
}
