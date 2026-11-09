package tests

import (
	"testing"
	"time"

	"kitchen-service/internal/queue"
)

var t0 = time.Date(2026, 11, 9, 12, 0, 0, 0, time.UTC)

func pending(id string, prep int, enqueuedMinutesAgo float64, now time.Time) queue.Order {
	return queue.Order{
		OrderID:     id,
		Status:      queue.StatusPendiente,
		PrepMinutes: prep,
		EnqueuedAt:  now.Add(-time.Duration(enqueuedMinutesAgo * float64(time.Minute))),
	}
}

func ids(ranked []queue.Ranked) []string {
	out := make([]string, len(ranked))
	for i, r := range ranked {
		out[i] = r.OrderID
	}
	return out
}

func assertOrder(t *testing.T, got []queue.Ranked, want ...string) {
	t.Helper()
	g := ids(got)
	if len(g) != len(want) {
		t.Fatalf("orden = %v, esperado %v", g, want)
	}
	for i := range want {
		if g[i] != want[i] {
			t.Fatalf("orden = %v, esperado %v", g, want)
		}
	}
}

func TestScoreFormula(t *testing.T) {
	if got := queue.Score(10, 4, 0.5); got != 8 {
		t.Fatalf("score = %v, esperado 8", got)
	}
}

func TestSJFShorterJobFirst(t *testing.T) {
	orders := []queue.Order{pending("largo", 15, 0, t0), pending("corto", 3, 0, t0), pending("medio", 8, 0, t0)}
	assertOrder(t, queue.Rank(orders, t0, 0.5), "corto", "medio", "largo")
}

func TestAgingOldOrderOvertakesNewShortOne(t *testing.T) {
	// El pedido largo lleva 70 min esperando: 30 - 0.5*70 = -5 < 3 del pedido nuevo y corto.
	orders := []queue.Order{pending("nuevo-corto", 3, 0, t0), pending("viejo-largo", 30, 70, t0)}
	assertOrder(t, queue.Rank(orders, t0, 0.5), "viejo-largo", "nuevo-corto")

	assertOrder(t, queue.Rank(orders, t0, 0), "nuevo-corto", "viejo-largo")
}

func TestAgingOvertakeHappensOverTime(t *testing.T) {
	largo := pending("largo", 20, 0, t0)
	corto := pending("corto", 5, 0, t0)
	assertOrder(t, queue.Rank([]queue.Order{largo, corto}, t0, 0.5), "corto", "largo")

	later := t0.Add(40 * time.Minute)
	nuevo := pending("corto-nuevo", 5, 0, later)
	assertOrder(t, queue.Rank([]queue.Order{largo, nuevo}, later, 0.5), "largo", "corto-nuevo")
}

func TestFIFOTieBreak(t *testing.T) {
	llegoSegundo := pending("b", 5, 2, t0)
	llegoPrimero := pending("a", 5, 7, t0)
	assertOrder(t, queue.Rank([]queue.Order{llegoSegundo, llegoPrimero}, t0, 0), "a", "b")
}

func TestStatusGroupsOrder(t *testing.T) {
	inPrep := pending("cocinando", 30, 1, t0)
	inPrep.Status = queue.StatusEnPreparacion
	ready := pending("listo", 1, 50, t0)
	ready.Status = queue.StatusListo
	orders := []queue.Order{ready, pending("pendiente", 2, 0, t0), inPrep}
	assertOrder(t, queue.Rank(orders, t0, 0.5), "cocinando", "pendiente", "listo")
}

func TestRankDeterministicOnFullTie(t *testing.T) {
	a, b := pending("a", 5, 1, t0), pending("b", 5, 1, t0)
	assertOrder(t, queue.Rank([]queue.Order{b, a}, t0, 0.5), "a", "b")
}

func TestETASimulationByCapacitySlots(t *testing.T) {
	started := t0.Add(-2 * time.Minute)
	cooking := pending("cocinando", 10, 5, t0)
	cooking.Status = queue.StatusEnPreparacion
	cooking.StartedAt = &started

	orders := []queue.Order{cooking, pending("p1", 4, 0, t0), pending("p2", 6, 0, t0)}
	ranked := queue.Rank(orders, t0, 0.5)

	etas := queue.ETAs(ranked, 2, t0)
	if etas["cocinando"] != 8 || etas["p1"] != 4 || etas["p2"] != 10 {
		t.Fatalf("etas = %v", etas)
	}

	etas = queue.ETAs(ranked, 1, t0)
	if etas["p1"] != 12 || etas["p2"] != 18 {
		t.Fatalf("etas con capacidad 1 = %v", etas)
	}
}

func TestETAReadyIsZero(t *testing.T) {
	ready := pending("listo", 5, 9, t0)
	ready.Status = queue.StatusListo
	etas := queue.ETAs(queue.Rank([]queue.Order{ready}, t0, 0.5), 4, t0)
	if etas["listo"] != 0 {
		t.Fatalf("eta listo = %d", etas["listo"])
	}
}
