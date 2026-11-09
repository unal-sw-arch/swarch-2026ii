package queue

import (
	"math"
	"sort"
	"time"
)

// ETAs simula `capacity` cupos: las pendientes toman el que se libera primero.
func ETAs(ranked []Ranked, capacity int, now time.Time) map[string]int {
	etas := make(map[string]int, len(ranked))
	slots := make([]float64, 0, capacity)

	for _, r := range ranked {
		switch r.Status {
		case StatusEnPreparacion:
			remaining := float64(r.PrepMinutes)
			if r.StartedAt != nil {
				remaining -= now.Sub(*r.StartedAt).Minutes()
			}
			if remaining < 0 {
				remaining = 0
			}
			slots = append(slots, remaining)
			etas[r.OrderID] = ceilMinutes(remaining)
		case StatusListo:
			etas[r.OrderID] = 0
		}
	}

	for len(slots) < capacity {
		slots = append(slots, 0)
	}
	if len(slots) == 0 {
		slots = append(slots, 0)
	}

	for _, r := range ranked {
		if r.Status != StatusPendiente {
			continue
		}
		sort.Float64s(slots)
		finish := slots[0] + float64(r.PrepMinutes)
		slots[0] = finish
		etas[r.OrderID] = ceilMinutes(finish)
	}
	return etas
}

func ceilMinutes(v float64) int {
	// Redondeo previo: evita sumar un minuto por ruido de punto flotante.
	return int(math.Ceil(math.Round(v*1000) / 1000))
}
