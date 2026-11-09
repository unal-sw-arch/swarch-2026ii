package queue

import (
	"context"
	"log/slog"
	"math"
	"time"
)

type PrepLookup interface {
	Lookup(ctx context.Context, productIDs []string) (map[string]int, error)
}

type EnqueueInput struct {
	OrderID    string
	CustomerID string
	OrderType  string
	PaidAt     time.Time
	// PrepMinutes <= 0 significa no informado.
	Items []OrderItem
}

// History registra una fila por transición (kitchen.order_status_events). changedBy nil = el sistema.
type History interface {
	Record(ctx context.Context, orderID, status string, changedBy *string, at time.Time) error
}

// EventSink recibe los eventos de dominio que genera la cola.
type EventSink interface {
	OrderReady(ctx context.Context, orderID, customerID string, readyAt time.Time, prepMinutes int) error
}

// Service reúne la lógica de la cola. Prep, CapacityFrom, History y Events son opcionales.
type Service struct {
	Store        *Store
	Prep         PrepLookup
	CapacityFrom CapacityProvider
	History      History
	Events       EventSink
	Log          *slog.Logger
	Aging        float64
	Capacity     int
	DefaultPrep  int
	Now          func() time.Time
}

func (s *Service) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

// ResolvePrepMinutes toma el máximo de los ítems; sin tiempo usa prep_times y luego DEFAULT_PREP_MINUTES.
func (s *Service) ResolvePrepMinutes(ctx context.Context, items []OrderItem) ([]OrderItem, int, error) {
	if len(items) == 0 {
		return items, s.DefaultPrep, nil
	}

	var missing []string
	for _, it := range items {
		if it.PrepMinutes <= 0 {
			missing = append(missing, it.ProductID)
		}
	}
	known := map[string]int{}
	if len(missing) > 0 && s.Prep != nil {
		found, err := s.Prep.Lookup(ctx, missing)
		if err != nil {
			return nil, 0, err
		}
		known = found
	}

	resolved := make([]OrderItem, len(items))
	maxPrep := 0
	for i, it := range items {
		if it.PrepMinutes <= 0 {
			if v, ok := known[it.ProductID]; ok && v > 0 {
				it.PrepMinutes = v
			} else {
				it.PrepMinutes = s.DefaultPrep
			}
		}
		resolved[i] = it
		if it.PrepMinutes > maxPrep {
			maxPrep = it.PrepMinutes
		}
	}
	return resolved, maxPrep, nil
}

func (s *Service) Enqueue(ctx context.Context, in EnqueueInput) (View, bool, error) {
	items, prep, err := s.ResolvePrepMinutes(ctx, in.Items)
	if err != nil {
		return View{}, false, err
	}

	now := s.now().UTC()
	order := Order{
		OrderID:     in.OrderID,
		CustomerID:  in.CustomerID,
		OrderType:   in.OrderType,
		Status:      StatusPendiente,
		PrepMinutes: prep,
		PaidAt:      in.PaidAt.UTC(),
		EnqueuedAt:  now,
		Items:       items,
	}
	created, err := s.Store.Enqueue(ctx, order, Score(prep, 0, s.Aging))
	if err != nil {
		return View{}, false, err
	}
	if created {
		s.record(ctx, order.OrderID, StatusPendiente, nil, now)
	}

	view, found, err := s.Get(ctx, in.OrderID)
	if err != nil {
		return View{}, false, err
	}
	if !found {
		return orderToView(Ranked{Order: order}, 0, 0), created, nil
	}
	return view, created, nil
}

// Snapshot reescribe los scores en Redis en cada consulta.
func (s *Service) Snapshot(ctx context.Context) ([]View, Capacity, error) {
	orders, err := s.Store.ListActive(ctx)
	if err != nil {
		return nil, Capacity{}, err
	}
	now := s.now()
	ranked := Rank(orders, now, s.Aging)
	capacity := s.capacity(ctx)
	etas := ETAs(ranked, capacity, now)

	views := make([]View, len(ranked))
	scores := make(map[string]float64, len(ranked))
	inPrep := 0
	for i, r := range ranked {
		views[i] = orderToView(r, i+1, etas[r.OrderID])
		scores[r.OrderID] = r.Score
		if r.Status == StatusEnPreparacion {
			inPrep++
		}
	}
	if err := s.Store.UpdateScores(ctx, scores); err != nil {
		return nil, Capacity{}, err
	}
	return views, Capacity{Max: capacity, InPreparation: inPrep}, nil
}

// Get devuelve sin posición ni ETA si la comanda ya fue entregada.
func (s *Service) Get(ctx context.Context, id string) (View, bool, error) {
	views, _, err := s.Snapshot(ctx)
	if err != nil {
		return View{}, false, err
	}
	for _, v := range views {
		if v.OrderID == id {
			return v, true, nil
		}
	}
	order, err := s.Store.Get(ctx, id)
	if err != nil || order == nil {
		return View{}, false, err
	}
	wait := WaitMinutes(order.EnqueuedAt, s.now())
	return orderToView(Ranked{Order: *order, WaitMinutes: wait, Score: Score(order.PrepMinutes, wait, s.Aging)}, 0, 0), true, nil
}

func orderToView(r Ranked, position, eta int) View {
	items := r.Items
	if items == nil {
		items = []OrderItem{}
	}
	return View{
		OrderID:       r.OrderID,
		CustomerID:    r.CustomerID,
		OrderType:     r.OrderType,
		Status:        r.Status,
		PrepMinutes:   r.PrepMinutes,
		PriorityScore: round2(r.Score),
		WaitMinutes:   round2(r.WaitMinutes),
		Position:      position,
		ETAMinutes:    eta,
		EnqueuedAt:    r.EnqueuedAt,
		StartedAt:     r.StartedAt,
		ReadyAt:       r.ReadyAt,
		Items:         items,
	}
}

func round2(v float64) float64 { return math.Round(v*100) / 100 }

func (s *Service) log() *slog.Logger {
	if s.Log != nil {
		return s.Log
	}
	return slog.Default()
}

// capacity lee la capacidad vigente; si la tabla no responde usa la configurada.
func (s *Service) capacity(ctx context.Context) int {
	if s.CapacityFrom != nil {
		if max, err := s.CapacityFrom.Capacity(ctx); err == nil && max > 0 {
			return max
		} else if err != nil {
			s.log().Warn("capacidad: se usa KITCHEN_CAPACITY", "error", err.Error())
		}
	}
	return s.Capacity
}

// record guarda el historial sin revertir el cambio de estado si PostgreSQL falla.
func (s *Service) record(ctx context.Context, orderID, status string, actor *string, at time.Time) {
	if s.History == nil {
		return
	}
	if err := s.History.Record(ctx, orderID, status, actor, at); err != nil {
		s.log().Error("historial: no se pudo registrar la transición", "order_id", orderID, "status", status, "error", err.Error())
	}
}

// ChangeStatus aplica una transición pedida por la cocina (PATCH .../status).
func (s *Service) ChangeStatus(ctx context.Context, id, to string, actor *string) (View, error) {
	switch to {
	case StatusEnPreparacion:
		return s.transition(ctx, id, StatusPendiente, to, actor, true)
	case StatusListo:
		return s.transition(ctx, id, StatusEnPreparacion, to, actor, false)
	}
	order, err := s.Store.Get(ctx, id)
	if err != nil {
		return View{}, err
	}
	if order == nil {
		return View{}, ErrNotFound
	}
	return View{}, &InvalidStateError{Current: order.Status, Requested: to}
}

// Deliver confirma la entrega de una comanda lista.
func (s *Service) Deliver(ctx context.Context, id string, actor *string) (View, error) {
	return s.transition(ctx, id, StatusListo, StatusEntregado, actor, false)
}

func (s *Service) transition(ctx context.Context, id, from, to string, actor *string, limited bool) (View, error) {
	capacity := -1
	if limited {
		capacity = s.capacity(ctx)
	}
	at := s.now().UTC()
	if err := s.Store.Transition(ctx, id, from, to, capacity, at, DeliveredTTL); err != nil {
		return View{}, err
	}
	s.record(ctx, id, to, actor, at)

	if to == StatusListo && s.Events != nil {
		if order, err := s.Store.Get(ctx, id); err == nil && order != nil {
			if err := s.Events.OrderReady(ctx, order.OrderID, order.CustomerID, at, order.PrepMinutes); err != nil {
				s.log().Error("order.ready: no se pudo encolar el evento", "order_id", id, "error", err.Error())
			}
		}
	}

	view, found, err := s.Get(ctx, id)
	if err != nil {
		return View{}, err
	}
	if !found {
		return View{}, ErrNotFound
	}
	return view, nil
}
