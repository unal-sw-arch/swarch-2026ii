package tests

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"

	httpapi "kitchen-service/internal/http"
	"kitchen-service/internal/queue"
)

const (
	serviceToken = "token-de-prueba"
	orderA       = "11111111-1111-4111-8111-111111111111"
	orderB       = "22222222-2222-4222-8222-222222222222"
	customer     = "33333333-3333-4333-8333-333333333333"
	product      = "44444444-4444-4444-8444-444444444444"
)

type env struct {
	srv  *httptest.Server
	mr   *miniredis.Miniredis
	now  time.Time
	hist *fakeHistory
	sink *fakeSink
	svc  *queue.Service
}

func newEnv(t *testing.T) *env { return newEnvCap(t, 4) }

func newEnvCap(t *testing.T, capacity int) *env {
	t.Helper()
	mr := miniredis.RunT(t)
	rdb := redis.NewClient(&redis.Options{Addr: mr.Addr()})
	t.Cleanup(func() { rdb.Close() })

	e := &env{mr: mr, now: t0, hist: &fakeHistory{}, sink: &fakeSink{}}
	store := queue.NewStore(rdb)
	svc := &queue.Service{
		Store: store, Aging: 0.5, Capacity: capacity, DefaultPrep: 5, Now: func() time.Time { return e.now },
		History: e.hist, Events: e.sink,
	}
	e.svc = svc
	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	h := &httpapi.Handler{
		Svc: svc, ServiceToken: serviceToken, Log: log,
		Ready: map[string]func(context.Context) error{"redis": store.Ping},
	}
	e.srv = httptest.NewServer(httpapi.NewRouter(h, log))
	t.Cleanup(e.srv.Close)
	return e
}

func (e *env) do(t *testing.T, method, path string, body any, headers map[string]string) (*http.Response, []byte) {
	t.Helper()
	var rd io.Reader
	if body != nil {
		raw, _ := json.Marshal(body)
		rd = bytes.NewReader(raw)
	}
	req, _ := http.NewRequest(method, e.srv.URL+path, rd)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	raw, _ := io.ReadAll(res.Body)
	return res, raw
}

func svcHeaders() map[string]string { return map[string]string{"X-Service-Token": serviceToken} }

func userHeaders(perms string) map[string]string {
	return map[string]string{"X-User-Id": customer, "X-Permissions": perms}
}

func orderBody(id string, prep int) map[string]any {
	return map[string]any{
		"order_id": id, "customer_id": customer, "order_type": "para_llevar",
		"paid_at": "2026-11-09T11:59:00Z",
		"items":   []map[string]any{{"product_id": product, "name": "Croissant", "quantity": 2, "prep_minutes": prep}},
	}
}

type errorBody struct {
	Code      string         `json:"code"`
	Message   string         `json:"message"`
	RequestID string         `json:"request_id"`
	Details   map[string]any `json:"details"`
}

func TestHealthAndReady(t *testing.T) {
	e := newEnv(t)
	res, body := e.do(t, "GET", "/health", nil, nil)
	if res.StatusCode != 200 || string(bytes.TrimSpace(body)) != `{"status":"ok"}` {
		t.Fatalf("health = %d %s", res.StatusCode, body)
	}
	res, body = e.do(t, "GET", "/ready", nil, nil)
	if res.StatusCode != 200 || string(bytes.TrimSpace(body)) != `{"status":"ready"}` {
		t.Fatalf("ready = %d %s", res.StatusCode, body)
	}
	e.mr.Close()
	res, body = e.do(t, "GET", "/ready", nil, nil)
	if res.StatusCode != 503 {
		t.Fatalf("ready sin redis = %d %s", res.StatusCode, body)
	}
	var out struct {
		Status       string            `json:"status"`
		Dependencies map[string]string `json:"dependencies"`
	}
	_ = json.Unmarshal(body, &out)
	if out.Status != "not_ready" || out.Dependencies["redis"] != "down" {
		t.Fatalf("ready body = %s", body)
	}
}

func TestEnqueueRequiresServiceToken(t *testing.T) {
	e := newEnv(t)
	for name, h := range map[string]map[string]string{
		"sin token":    nil,
		"token errado": {"X-Service-Token": "otro"},
		"solo usuario": userHeaders("kitchen:update"),
	} {
		res, body := e.do(t, "POST", "/queue/orders", orderBody(orderA, 5), h)
		var eb errorBody
		_ = json.Unmarshal(body, &eb)
		if res.StatusCode != 401 || eb.Code != "UNAUTHENTICATED" || eb.RequestID == "" {
			t.Fatalf("%s: %d %s", name, res.StatusCode, body)
		}
	}
}

func TestEnqueueIsIdempotent(t *testing.T) {
	e := newEnv(t)
	res, body := e.do(t, "POST", "/queue/orders", orderBody(orderA, 5), svcHeaders())
	if res.StatusCode != 201 {
		t.Fatalf("primer POST = %d %s", res.StatusCode, body)
	}
	var first queue.View
	_ = json.Unmarshal(body, &first)
	if first.Status != "pendiente" || first.PrepMinutes != 5 || first.Position != 1 || first.ETAMinutes != 5 {
		t.Fatalf("view = %+v", first)
	}

	e.now = e.now.Add(3 * time.Minute)
	res, body = e.do(t, "POST", "/queue/orders", orderBody(orderA, 99), svcHeaders())
	if res.StatusCode != 200 {
		t.Fatalf("segundo POST = %d %s", res.StatusCode, body)
	}
	var second queue.View
	_ = json.Unmarshal(body, &second)
	if second.PrepMinutes != 5 || !second.EnqueuedAt.Equal(first.EnqueuedAt) {
		t.Fatalf("el existente debe quedar intacto: %+v", second)
	}

	res, body = e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read,kitchen:update"))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(body, &q)
	if res.StatusCode != 200 || len(q.Items) != 1 {
		t.Fatalf("cola = %d %s", res.StatusCode, body)
	}
}

func TestEnqueueWithoutItemsUsesDefault(t *testing.T) {
	e := newEnv(t)
	body := orderBody(orderA, 0)
	delete(body, "items")
	res, raw := e.do(t, "POST", "/queue/orders", body, svcHeaders())
	var v queue.View
	_ = json.Unmarshal(raw, &v)
	if res.StatusCode != 201 || v.PrepMinutes != 5 || len(v.Items) != 0 {
		t.Fatalf("%d %s", res.StatusCode, raw)
	}
}

func TestEnqueuePrepIsMaxOfItems(t *testing.T) {
	e := newEnv(t)
	body := orderBody(orderA, 4)
	body["items"] = []map[string]any{
		{"product_id": product, "name": "Pan", "quantity": 1, "prep_minutes": 4},
		{"product_id": product, "name": "Desayuno", "quantity": 1, "prep_minutes": 12},
		{"product_id": product, "name": "Sin tiempo", "quantity": 1},
	}
	res, raw := e.do(t, "POST", "/queue/orders", body, svcHeaders())
	var v queue.View
	_ = json.Unmarshal(raw, &v)
	if res.StatusCode != 201 || v.PrepMinutes != 12 {
		t.Fatalf("%d %s", res.StatusCode, raw)
	}
	if v.Items[2].PrepMinutes != 5 {
		t.Fatalf("el ítem sin tiempo debe usar el default: %+v", v.Items)
	}
}

func TestEnqueueAcceptsOrderServiceSlugsAndStaffOrders(t *testing.T) {
	e := newEnv(t)
	body := orderBody(orderA, 3)
	body["customer_id"] = nil
	body["items"] = []map[string]any{
		{"product_id": "p-cafe-americano", "name": "Café americano", "quantity": 1, "prep_minutes": 1},
	}
	res, raw := e.do(t, "POST", "/queue/orders", body, svcHeaders())
	if res.StatusCode != 201 {
		t.Fatalf("POST = %d %s", res.StatusCode, raw)
	}
	var view queue.View
	if err := json.Unmarshal(raw, &view); err != nil {
		t.Fatal(err)
	}
	if view.OrderID != orderA || view.OrderType != "para_llevar" || view.CustomerID != "" {
		t.Fatalf("vista = %+v", view)
	}
	if len(view.Items) != 1 || view.Items[0].ProductID != "p-cafe-americano" {
		t.Fatalf("items = %+v", view.Items)
	}
}

func TestEnqueueValidation(t *testing.T) {
	e := newEnv(t)
	bad := orderBody("no-es-uuid", 5)
	bad["order_type"] = "domicilio"
	bad["paid_at"] = "ayer"
	res, raw := e.do(t, "POST", "/queue/orders", bad, svcHeaders())
	var eb errorBody
	_ = json.Unmarshal(raw, &eb)
	if res.StatusCode != 400 || eb.Code != "VALIDATION_ERROR" {
		t.Fatalf("%d %s", res.StatusCode, raw)
	}
	for _, field := range []string{"order_id", "order_type", "paid_at"} {
		if _, ok := eb.Details[field]; !ok {
			t.Fatalf("falta detalle de %s: %s", field, raw)
		}
	}

	req, _ := http.NewRequest("POST", e.srv.URL+"/queue/orders", bytes.NewBufferString("{no json"))
	req.Header.Set("X-Service-Token", serviceToken)
	res2, _ := http.DefaultClient.Do(req)
	if res2.StatusCode != 400 {
		t.Fatalf("JSON roto = %d", res2.StatusCode)
	}
}

func TestGetQueueAuthorization(t *testing.T) {
	e := newEnv(t)
	e.do(t, "POST", "/queue/orders", orderBody(orderA, 5), svcHeaders())

	res, _ := e.do(t, "GET", "/queue", nil, nil)
	if res.StatusCode != 401 {
		t.Fatalf("sin identidad = %d", res.StatusCode)
	}
	res, _ = e.do(t, "GET", "/queue", nil, userHeaders("orders:read_own"))
	if res.StatusCode != 403 {
		t.Fatalf("sin permiso = %d", res.StatusCode)
	}
	res, _ = e.do(t, "GET", "/queue?status=cocinando", nil, userHeaders("kitchen:read,kitchen:update"))
	if res.StatusCode != 400 {
		t.Fatalf("status inválido = %d", res.StatusCode)
	}
}

func TestGetQueueDispatcherSeesOnlyReady(t *testing.T) {
	e := newEnv(t)
	e.do(t, "POST", "/queue/orders", orderBody(orderA, 5), svcHeaders())
	e.do(t, "POST", "/queue/orders", orderBody(orderB, 5), svcHeaders())

	cookHeaders := userHeaders("kitchen:read,kitchen:update")
	res, _ := e.do(t, "PATCH", "/queue/orders/"+orderA+"/status", map[string]string{"status": "en_preparacion"}, cookHeaders)
	if res.StatusCode != 200 {
		t.Fatalf("iniciar preparación = %d", res.StatusCode)
	}
	res, _ = e.do(t, "PATCH", "/queue/orders/"+orderA+"/status", map[string]string{"status": "listo"}, cookHeaders)
	if res.StatusCode != 200 {
		t.Fatalf("marcar como listo = %d", res.StatusCode)
	}

	res, raw := e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read,kitchen:deliver"))
	var q struct {
		Items    []queue.View
		Capacity queue.Capacity
	}
	_ = json.Unmarshal(raw, &q)
	if res.StatusCode != 200 || len(q.Items) != 1 || q.Items[0].OrderID != orderA || q.Items[0].Status != queue.StatusListo || q.Capacity.Max != 4 {
		t.Fatalf("%d %s", res.StatusCode, raw)
	}
	res, raw = e.do(t, "GET", "/queue", nil, cookHeaders)
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 2 {
		t.Fatalf("cocinero debe ver ambos pedidos, incluido el pendiente: %s", raw)
	}
}

func TestQueueOrderSJFAndFIFOOverHTTP(t *testing.T) {
	e := newEnv(t)
	e.do(t, "POST", "/queue/orders", orderBody(orderA, 10), svcHeaders())
	e.now = e.now.Add(time.Minute)
	e.do(t, "POST", "/queue/orders", orderBody(orderB, 3), svcHeaders())

	_, raw := e.do(t, "GET", "/queue", nil, userHeaders("kitchen:read,kitchen:update"))
	var q struct{ Items []queue.View }
	_ = json.Unmarshal(raw, &q)
	if len(q.Items) != 2 || q.Items[0].OrderID != orderB || q.Items[1].OrderID != orderA {
		t.Fatalf("SJF: %s", raw)
	}
	if q.Items[0].Position != 1 || q.Items[1].Position != 2 {
		t.Fatalf("posiciones: %s", raw)
	}
}

func TestUnknownRouteUsesUniformError(t *testing.T) {
	e := newEnv(t)
	res, raw := e.do(t, "GET", "/nada", nil, nil)
	var eb errorBody
	_ = json.Unmarshal(raw, &eb)
	if res.StatusCode != 404 || eb.Code != "NOT_FOUND" || eb.RequestID == "" {
		t.Fatalf("%d %s", res.StatusCode, raw)
	}
}
