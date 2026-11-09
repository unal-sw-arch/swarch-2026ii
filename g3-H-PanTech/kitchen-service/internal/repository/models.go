package repository

import "time"

type StatusEvent struct {
	OrderID   string
	Status    string
	ChangedAt time.Time
	ChangedBy *string
}
