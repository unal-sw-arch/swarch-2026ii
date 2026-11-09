// Package queue implementa la cola priorizada de cocina.
package queue

import "time"

const (
	StatusPendiente     = "pendiente"
	StatusEnPreparacion = "en_preparacion"
	StatusListo         = "listo"
	StatusEntregado     = "entregado"
)

const (
	OrderTypeParaLlevar = "para_llevar"
	OrderTypeEnMesa     = "en_mesa"
)

type OrderItem struct {
	ProductID   string `json:"product_id"`
	Name        string `json:"name"`
	Quantity    int    `json:"quantity"`
	PrepMinutes int    `json:"prep_minutes"`
}

type Order struct {
	OrderID     string
	CustomerID  string
	OrderType   string
	Status      string
	PrepMinutes int
	PaidAt      time.Time
	EnqueuedAt  time.Time
	StartedAt   *time.Time
	ReadyAt     *time.Time
	Items       []OrderItem
}

type View struct {
	OrderID       string      `json:"order_id"`
	CustomerID    string      `json:"customer_id"`
	OrderType     string      `json:"order_type"`
	Status        string      `json:"status"`
	PrepMinutes   int         `json:"prep_minutes"`
	PriorityScore float64     `json:"priority_score"`
	WaitMinutes   float64     `json:"wait_minutes"`
	Position      int         `json:"position"`
	ETAMinutes    int         `json:"eta_minutes"`
	EnqueuedAt    time.Time   `json:"enqueued_at"`
	StartedAt     *time.Time  `json:"started_at"`
	ReadyAt       *time.Time  `json:"ready_at"`
	Items         []OrderItem `json:"items"`
}

type Capacity struct {
	Max           int `json:"max"`
	InPreparation int `json:"in_preparation"`
}
