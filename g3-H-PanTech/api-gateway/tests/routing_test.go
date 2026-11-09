package tests

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"

	"api-gateway/internal/config"
	"api-gateway/internal/router"
)

type seen struct {
	service string
	method  string
	path    string
	query   string
	header  http.Header
}

type harness struct {
	gateway *httptest.Server
	mr      *miniredis.Miniredis
	last    *seen
}

func fakeService(t *testing.T, name string, last **seen) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		*last = &seen{service: name, method: r.Method, path: r.URL.Path, query: r.URL.RawQuery, header: r.Header.Clone()}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"service":"`+name+`"}`)
	}))
	t.Cleanup(srv.Close)
	return srv
}

func mustURL(t *testing.T, raw string) *url.URL {
	t.Helper()
	u, err := url.Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	return u
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	h := &harness{mr: miniredis.RunT(t)}
	rdb := redis.NewClient(&redis.Options{Addr: h.mr.Addr()})
	t.Cleanup(func() { rdb.Close() })

	cfg := config.Config{
		Port:         "8080",
		IdentityURL:  mustURL(t, fakeService(t, "identity", &h.last).URL),
		OrderURL:     mustURL(t, fakeService(t, "order", &h.last).URL),
		KitchenURL:   mustURL(t, fakeService(t, "kitchen", &h.last).URL),
		ReportingURL: mustURL(t, fakeService(t, "reporting", &h.last).URL),
	}
	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	h.gateway = httptest.NewServer(router.New(cfg, rdb, log))
	t.Cleanup(h.gateway.Close)
	return h
}

func (h *harness) do(t *testing.T, method, path string, headers map[string]string) (*http.Response, string) {
	t.Helper()
	req, _ := http.NewRequest(method, h.gateway.URL+path, nil)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	raw, _ := io.ReadAll(res.Body)
	return res, string(raw)
}

type apiError struct {
	Code      string         `json:"code"`
	Message   string         `json:"message"`
	RequestID string         `json:"request_id"`
	Details   map[string]any `json:"details"`
}

func TestHealthAndReady(t *testing.T) {
	h := newHarness(t)
	res, body := h.do(t, "GET", "/health", nil)
	if res.StatusCode != 200 || strings.TrimSpace(body) != `{"status":"ok"}` {
		t.Fatalf("health = %d %s", res.StatusCode, body)
	}
	res, body = h.do(t, "GET", "/ready", nil)
	if res.StatusCode != 200 || strings.TrimSpace(body) != `{"status":"ready"}` {
		t.Fatalf("ready = %d %s", res.StatusCode, body)
	}
	h.mr.Close()
	res, body = h.do(t, "GET", "/ready", nil)
	if res.StatusCode != 503 || !strings.Contains(body, `"not_ready"`) || !strings.Contains(body, `"redis":"down"`) {
		t.Fatalf("ready sin redis = %d %s", res.StatusCode, body)
	}
}

func TestRoutingByPrefix(t *testing.T) {
	cases := []struct {
		method, path, service string
	}{
		{"POST", "/auth/login", "identity"},
		{"GET", "/users/abc", "identity"},
		{"GET", "/roles", "identity"},
		{"GET", "/permissions", "identity"},
		{"GET", "/audit", "identity"},
		{"GET", "/menu", "order"},
		{"POST", "/orders", "order"},
		{"POST", "/orders/123/payment", "order"},
		{"GET", "/reservations", "order"},
		{"GET", "/products", "order"},
		{"GET", "/queue", "kitchen"},
		{"PATCH", "/queue/orders/1/status", "kitchen"},
		{"POST", "/queue/orders/1/deliver", "kitchen"},
		{"GET", "/history", "reporting"},
		{"GET", "/reports/sales", "reporting"},
	}
	for _, c := range cases {
		t.Run(c.method+" "+c.path, func(t *testing.T) {
			h := newHarness(t)
			res, body := h.do(t, c.method, c.path+"?x=1", nil)
			if res.StatusCode != 200 || h.last == nil || h.last.service != c.service {
				t.Fatalf("status=%d body=%s last=%+v", res.StatusCode, body, h.last)
			}
			if h.last.path != c.path || h.last.query != "x=1" || h.last.method != c.method {
				t.Fatalf("no se conservó la ruta: %+v", h.last)
			}
		})
	}
}

func TestUnknownPrefixIsUniform404(t *testing.T) {
	h := newHarness(t)
	for _, path := range []string{"/", "/nada", "/queues", "/authx", "/.well-known/jwks.json"} {
		res, body := h.do(t, "GET", path, nil)
		var e apiError
		_ = json.Unmarshal([]byte(body), &e)
		if res.StatusCode != 404 || e.Code != "NOT_FOUND" || e.RequestID == "" || h.last != nil {
			t.Fatalf("%s: %d %s (last=%+v)", path, res.StatusCode, body, h.last)
		}
	}
}

func TestJWKSIsNotExposed(t *testing.T) {
	h := newHarness(t)
	res, _ := h.do(t, "GET", "/auth/../.well-known/jwks.json", nil)
	if h.last != nil && h.last.service == "identity" && strings.Contains(h.last.path, "jwks") {
		t.Fatalf("el JWKS no debe alcanzarse por el Gateway (status %d, path %s)", res.StatusCode, h.last.path)
	}
}

func TestExternalEnqueueIsBlocked(t *testing.T) {
	h := newHarness(t)
	for _, path := range []string{"/queue/orders", "/queue/orders/"} {
		res, body := h.do(t, "POST", path, map[string]string{"X-Service-Token": "lo-que-sea"})
		var e apiError
		_ = json.Unmarshal([]byte(body), &e)
		if res.StatusCode != 403 || e.Code != "FORBIDDEN" || e.RequestID == "" {
			t.Fatalf("%s: %d %s", path, res.StatusCode, body)
		}
		if h.last != nil {
			t.Fatalf("la petición bloqueada llegó a %s", h.last.service)
		}
	}
	if res, _ := h.do(t, "GET", "/queue", nil); res.StatusCode != 200 {
		t.Fatalf("GET /queue = %d", res.StatusCode)
	}
}

func TestClientIdentityHeadersAreStripped(t *testing.T) {
	h := newHarness(t)
	spoofed := map[string]string{
		"X-User-Id":       "00000000-0000-0000-0000-000000000000",
		"X-User-Email":    "intruso@evil.com",
		"X-Roles":         "admin",
		"X-Permissions":   "kitchen:update,users:manage",
		"X-Level":         "100",
		"X-Service-Token": "secreto-robado",
		"X-Request-Id":    "id-falso",
		"Authorization":   "Bearer abc",
	}
	res, _ := h.do(t, "GET", "/menu", spoofed)
	if res.StatusCode != 200 || h.last == nil {
		t.Fatalf("status %d", res.StatusCode)
	}
	for _, name := range []string{"X-User-Id", "X-User-Email", "X-Roles", "X-Permissions", "X-Level", "X-Service-Token"} {
		if v := h.last.header.Get(name); v != "" {
			t.Fatalf("%s llegó al servicio con %q", name, v)
		}
	}
	if got := h.last.header.Get("X-Request-Id"); got == "" || got == "id-falso" || got != res.Header.Get("X-Request-Id") {
		t.Fatalf("X-Request-Id = %q (respuesta %q)", got, res.Header.Get("X-Request-Id"))
	}
	if h.last.header.Get("Authorization") != "Bearer abc" {
		t.Fatal("Authorization debe conservarse para Identity")
	}
}

func TestUpstreamDownReturnsUniformError(t *testing.T) {
	h := newHarness(t)
	cfg := config.Config{
		IdentityURL:  mustURL(t, "http://127.0.0.1:1"),
		OrderURL:     mustURL(t, "http://127.0.0.1:1"),
		KitchenURL:   mustURL(t, "http://127.0.0.1:1"),
		ReportingURL: mustURL(t, "http://127.0.0.1:1"),
	}
	rdb := redis.NewClient(&redis.Options{Addr: h.mr.Addr()})
	t.Cleanup(func() { rdb.Close() })
	srv := httptest.NewServer(router.New(cfg, rdb, slog.New(slog.NewJSONHandler(io.Discard, nil))))
	t.Cleanup(srv.Close)

	res, err := http.Get(srv.URL + "/menu")
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	var e apiError
	_ = json.NewDecoder(res.Body).Decode(&e)
	if (res.StatusCode != 502 && res.StatusCode != 503) || e.Code == "" || e.RequestID == "" || e.Details == nil {
		t.Fatalf("%d %+v", res.StatusCode, e)
	}
}
