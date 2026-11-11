package queue

import (
	"sort"
	"time"
)

type Ranked struct {
	Order
	WaitMinutes float64
	Score       float64
}

func WaitMinutes(enqueuedAt, now time.Time) float64 {
	d := now.Sub(enqueuedAt).Minutes()
	if d < 0 {
		return 0
	}
	return d
}

// Score aplica SJF con aging (menor = primero): prep - aging * espera.
func Score(prepMinutes int, waitMinutes, agingFactor float64) float64 {
	return float64(prepMinutes) - agingFactor*waitMinutes
}

func groupRank(status string) int {
	switch status {
	case StatusEnPreparacion:
		return 0
	case StatusPendiente:
		return 1
	case StatusListo:
		return 2
	default:
		return 3
	}
}

// Rank ordena: en_preparacion, luego pendiente por score (FIFO en empate) y luego listo.
func Rank(orders []Order, now time.Time, agingFactor float64) []Ranked {
	ranked := make([]Ranked, len(orders))
	for i, o := range orders {
		wait := WaitMinutes(o.EnqueuedAt, now)
		ranked[i] = Ranked{Order: o, WaitMinutes: wait, Score: Score(o.PrepMinutes, wait, agingFactor)}
	}

	sort.SliceStable(ranked, func(i, j int) bool {
		a, b := ranked[i], ranked[j]
		if ga, gb := groupRank(a.Status), groupRank(b.Status); ga != gb {
			return ga < gb
		}
		if a.Status == StatusPendiente && a.Score != b.Score {
			return a.Score < b.Score
		}
		if !a.EnqueuedAt.Equal(b.EnqueuedAt) {
			return a.EnqueuedAt.Before(b.EnqueuedAt)
		}
		return a.OrderID < b.OrderID
	})
	return ranked
}
