package queue

import (
	"context"
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

type Service struct {
	Store       *Store
	Prep        PrepLookup
	Aging       float64
	Capacity    int
	DefaultPrep int
	Now         func() time.Time
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
	etas := ETAs(ranked, s.Capacity, now)

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
	return views, Capacity{Max: s.Capacity, InPreparation: inPrep}, nil
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
