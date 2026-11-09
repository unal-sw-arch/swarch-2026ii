package tests

import (
	"encoding/json"
	"fmt"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"kitchen-service/internal/queue"
)

const (
	cookPerms     = "kitchen:read,kitchen:update"
	dispatchPerms = "kitchen:deliver"
	orderC        = "55555555-5555-4555-8555-555555555555"
)

func (e *env) enqueue(t *testing.T, id string, prep int) {
	t.Helper()
	if res, body := e.do(t, "POST", "/queue/orders", orderBody(id, prep), svcHeaders()); res.StatusCode != 201 {
		t.Fatalf("encolar %s: %d %s", id, res.StatusCode, body)
	}
}

func (e *env) setStatus(t *testing.T, id, status, perms string) (int, errorBody, queue.View) {
	t.Helper()
	res, raw := e.do(t, "PATCH", "/queue/orders/"+id+"/status", map[string]string{"status": status}, userHeaders(perms))
	var eb errorBody
	var v queue.View
	_ = json.Unmarshal(raw, &eb)
	_ = json.Unmarshal(raw, &v)
	return res.StatusCode, eb, v
}

func (e *env) deliver(t *testing.T, id, perms string) (int, errorBody, queue.View) {
	t.Helper()
	res, raw := e.do(t, "POST", "/queue/orders/"+id+"/deliver", nil, userHeaders(perms))
	var eb errorBody
	var v queue.View
	_ = json.Unmarshal(raw, &eb)
	_ = json.Unmarshal(raw, &v)
	return res.StatusCode, eb, v
}

func TestFullLifecycleHistoryAndEvent(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 8)

	e.now = e.now.Add(time.Minute)
	status, _, v := e.setStatus(t, orderA, "en_preparacion", cookPerms)
	if status != 200 || v.Status != "en_preparacion" || v.StartedAt == nil {
		t.Fatalf("iniciar: %d %+v", status, v)
	}

	e.now = e.now.Add(8 * time.Minute)
	status, _, v = e.setStatus(t, orderA, "listo", cookPerms)
	if status != 200 || v.Status != "listo" || v.ReadyAt == nil || v.ETAMinutes != 0 {
		t.Fatalf("listo: %d %+v", status, v)
	}
	if e.sink.count() != 1 {
		t.Fatalf("order.ready debe generarse una vez, hubo %d", e.sink.count())
	}
	ev := e.sink.events[0]
	if ev.OrderID != orderA || ev.CustomerID != customer || ev.PrepMinutes != 8 || !ev.ReadyAt.Equal(v.ReadyAt.UTC()) {
		t.Fatalf("evento = %+v", ev)
	}

	status, _, v = e.deliver(t, orderA, dispatchPerms)
	if status != 200 || v.Status != "entregado" || v.Position != 0 {
		t.Fatalf("entregar: %d %+v", status, v)
	}

	want := []string{"pendiente", "en_preparacion", "listo", "entregado"}
	got := e.hist.statuses(orderA)
	if fmt.Sprint(got) != fmt.Sprint(want) {
		t.Fatalf("historial = %v, esperado %v", got, want)
	}
	for _, r := range e.hist.rows[1:] {
		if r.ChangedBy == nil || *r.ChangedBy != customer {
			t.Fatalf("changed_by debe ser X-User-Id: %+v", r)
		}
	}
	if e.hist.rows[0].ChangedBy != nil {
		t.Fatal("el encolado lo hace el sistema (changed_by nulo)")
	}

	_, raw := e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read"))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 0 {
		t.Fatalf("lo entregado sale de la cola activa: %s", raw)
	}
}

func TestInvalidTransitionsReturn409(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 5)

	for _, status := range []string{"listo", "pendiente", "entregado"} {
		code, eb, _ := e.setStatus(t, orderA, status, cookPerms)
		if code != 409 || eb.Code != "INVALID_STATE" || eb.Details["current_status"] != "pendiente" {
			t.Fatalf("pendiente -> %s: %d %+v", status, code, eb)
		}
	}

	if code, _, _ := e.setStatus(t, orderA, "en_preparacion", cookPerms); code != 200 {
		t.Fatalf("iniciar = %d", code)
	}
	if code, eb, _ := e.setStatus(t, orderA, "en_preparacion", cookPerms); code != 409 || eb.Code != "INVALID_STATE" {
		t.Fatalf("repetir iniciar: %d %+v", code, eb)
	}
	if code, eb, _ := e.deliver(t, orderA, dispatchPerms); code != 409 || eb.Code != "INVALID_STATE" {
		t.Fatalf("entregar sin estar listo: %d %+v", code, eb)
	}
	if code, _, _ := e.setStatus(t, orderA, "listo", cookPerms); code != 200 {
		t.Fatalf("listo = %d", code)
	}
	if code, eb, _ := e.setStatus(t, orderA, "en_preparacion", cookPerms); code != 409 {
		t.Fatalf("retroceder: %d %+v", code, eb)
	}
	if code, _, _ := e.deliver(t, orderA, dispatchPerms); code != 200 {
		t.Fatal("entregar")
	}
	if code, eb, _ := e.deliver(t, orderA, dispatchPerms); code != 409 {
		t.Fatalf("doble entrega: %d %+v", code, eb)
	}
	if e.sink.count() != 1 {
		t.Fatalf("un solo order.ready, hubo %d", e.sink.count())
	}
}

func TestUnknownOrderAndBadStatus(t *testing.T) {
	e := newEnv(t)
	if code, eb, _ := e.setStatus(t, orderA, "en_preparacion", cookPerms); code != 404 || eb.Code != "NOT_FOUND" {
		t.Fatalf("inexistente: %d %+v", code, eb)
	}
	if code, eb, _ := e.deliver(t, orderA, dispatchPerms); code != 404 || eb.Code != "NOT_FOUND" {
		t.Fatalf("entregar inexistente: %d %+v", code, eb)
	}
	res, _ := e.do(t, "PATCH", "/queue/orders/no-es-uuid/status", map[string]string{"status": "listo"}, userHeaders(cookPerms))
	if res.StatusCode != 404 {
		t.Fatalf("id mal formado = %d", res.StatusCode)
	}
	e.enqueue(t, orderA, 5)
	if code, eb, _ := e.setStatus(t, orderA, "cocinando", cookPerms); code != 400 || eb.Code != "VALIDATION_ERROR" {
		t.Fatalf("status inválido: %d %+v", code, eb)
	}
}

func TestCapacityBlocksWithoutLosingTheOrder(t *testing.T) {
	e := newEnvCap(t, 2)
	e.enqueue(t, orderA, 5)
	e.enqueue(t, orderB, 5)
	e.enqueue(t, orderC, 5)

	for _, id := range []string{orderA, orderB} {
		if code, _, _ := e.setStatus(t, id, "en_preparacion", cookPerms); code != 200 {
			t.Fatalf("%s debe poder iniciar", id)
		}
	}
	code, eb, _ := e.setStatus(t, orderC, "en_preparacion", cookPerms)
	if code != 409 || eb.Code != "KITCHEN_AT_CAPACITY" || eb.Details["max"] != float64(2) || eb.Details["in_preparation"] != float64(2) {
		t.Fatalf("cocina llena: %d %+v", code, eb)
	}

	_, raw := e.do(t, "GET", "/queue/orders/"+orderC, nil, userHeaders("kitchen:read"))
	var v queue.View
	_ = json.Unmarshal(raw, &v)
	if v.Status != "pendiente" {
		t.Fatalf("el pedido debe seguir pendiente: %s", raw)
	}

	if code, _, _ := e.setStatus(t, orderA, "listo", cookPerms); code != 200 {
		t.Fatal("listo")
	}
	if code, _, _ := e.setStatus(t, orderC, "en_preparacion", cookPerms); code != 200 {
		t.Fatal("con cupo libre debe iniciar")
	}

	_, raw = e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read"))
	var q struct {
		Items    []queue.View
		Capacity queue.Capacity
	}
	_ = json.Unmarshal(raw, &q)
	if q.Capacity.Max != 2 || q.Capacity.InPreparation != 2 {
		t.Fatalf("capacidad: %+v", q.Capacity)
	}
	if q.Items[len(q.Items)-1].Status != "listo" {
		t.Fatalf("los listos van al final: %s", raw)
	}
}

func TestCapacityRaceNeverExceedsMax(t *testing.T) {
	const capacity, orders = 3, 12
	e := newEnvCap(t, capacity)
	ids := make([]string, orders)
	for i := range ids {
		ids[i] = fmt.Sprintf("00000000-0000-4000-8000-%012d", i+1)
		e.enqueue(t, ids[i], 5)
	}

	var ok, full atomic.Int32
	var wg sync.WaitGroup
	for _, id := range ids {
		wg.Add(1)
		go func() {
			defer wg.Done()
			code, eb, _ := e.setStatus(t, id, "en_preparacion", cookPerms)
			switch {
			case code == 200:
				ok.Add(1)
			case code == 409 && eb.Code == "KITCHEN_AT_CAPACITY":
				full.Add(1)
			default:
				t.Errorf("respuesta inesperada: %d %+v", code, eb)
			}
		}()
	}
	wg.Wait()
	if ok.Load() != capacity || full.Load() != orders-capacity {
		t.Fatalf("iniciaron %d (máx %d), rechazados %d", ok.Load(), capacity, full.Load())
	}
}

func TestTransitionPermissions(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 5)

	res, _ := e.do(t, "PATCH", "/queue/orders/"+orderA+"/status", map[string]string{"status": "en_preparacion"}, nil)
	if res.StatusCode != 401 {
		t.Fatalf("PATCH sin identidad = %d", res.StatusCode)
	}
	res, _ = e.do(t, "POST", "/queue/orders/"+orderA+"/deliver", nil, nil)
	if res.StatusCode != 401 {
		t.Fatalf("deliver sin identidad = %d", res.StatusCode)
	}
	res, _ = e.do(t, "GET", "/queue/orders/"+orderA, nil, nil)
	if res.StatusCode != 401 {
		t.Fatalf("detalle sin identidad = %d", res.StatusCode)
	}

	denied := []struct {
		name  string
		code  int
		eb    errorBody
		perms string
	}{}
	for _, perms := range []string{dispatchPerms, "kitchen:read", "orders:read_own", ""} {
		code, eb, _ := e.setStatus(t, orderA, "en_preparacion", perms)
		denied = append(denied, struct {
			name  string
			code  int
			eb    errorBody
			perms string
		}{"PATCH", code, eb, perms})
	}
	for _, perms := range []string{cookPerms, "orders:read_own", ""} {
		code, eb, _ := e.deliver(t, orderA, perms)
		denied = append(denied, struct {
			name  string
			code  int
			eb    errorBody
			perms string
		}{"deliver", code, eb, perms})
	}
	for _, d := range denied {
		if d.code != 403 || d.eb.Code != "FORBIDDEN" {
			t.Fatalf("%s con %q: %d %+v", d.name, d.perms, d.code, d.eb)
		}
	}

	if v, _, _ := e.svc.Get(t.Context(), orderA); v.Status != "pendiente" {
		t.Fatalf("las denegadas no deben cambiar nada: %s", v.Status)
	}
}

func TestDispatcherSeesOnlyReadyOrderDetail(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 5)

	res, _ := e.do(t, "GET", "/queue/orders/"+orderA, nil, userHeaders(dispatchPerms))
	if res.StatusCode != 404 {
		t.Fatalf("pendiente para despachador debe ser 404, fue %d", res.StatusCode)
	}
	e.setStatus(t, orderA, "en_preparacion", cookPerms)
	e.setStatus(t, orderA, "listo", cookPerms)

	res, raw := e.do(t, "GET", "/queue/orders/"+orderA, nil, userHeaders(dispatchPerms))
	var v queue.View
	_ = json.Unmarshal(raw, &v)
	if res.StatusCode != 200 || v.Status != "listo" {
		t.Fatalf("listo para despachador: %d %s", res.StatusCode, raw)
	}

	_, raw = e.do(t, "GET", "/queue", nil, userHeaders(dispatchPerms))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 1 || q.Items[0].OrderID != orderA {
		t.Fatalf("el despachador debe ver el listo: %s", raw)
	}
}

func TestQueueStatusFilter(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 5)
	e.enqueue(t, orderB, 9)
	e.setStatus(t, orderA, "en_preparacion", cookPerms)

	_, raw := e.do(t, "GET", "/queue?status=pendiente", nil, userHeaders("kitchen:read"))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 1 || q.Items[0].OrderID != orderB {
		t.Fatalf("filtro pendiente: %s", raw)
	}
	_, raw = e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read"))
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 2 || q.Items[0].Status != "en_preparacion" {
		t.Fatalf("en_preparacion primero: %s", raw)
	}
}

func TestHistoryFailureDoesNotRevertState(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 5)
	e.hist.fail = true
	if code, _, _ := e.setStatus(t, orderA, "en_preparacion", cookPerms); code != 200 {
		t.Fatalf("la transición no depende del historial: %d", code)
	}
}

func TestAgingChangesQueueOrderOverTime(t *testing.T) {
	e := newEnv(t)
	e.enqueue(t, orderA, 30)
	e.now = e.now.Add(70 * time.Minute)
	e.enqueue(t, orderB, 3)

	_, raw := e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read"))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(raw, &q)
	if q.Items[0].OrderID != orderA || q.Items[1].OrderID != orderB {
		t.Fatalf("el pedido viejo debe pasar al corto: %s", raw)
	}
	if q.Items[0].PriorityScore >= q.Items[1].PriorityScore {
		t.Fatalf("scores: %v vs %v", q.Items[0].PriorityScore, q.Items[1].PriorityScore)
	}
}
