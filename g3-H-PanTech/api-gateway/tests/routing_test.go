package tests

import (
	"net/http"
	"strings"
	"testing"
)

func stringsReader(s string) *strings.Reader { return strings.NewReader(s) }

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
		{"GET", "/auth/me", "identity"},
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
			res, body := h.do(t, c.method, c.path+"?x=1", h.bearer(allPerms...))
			last := h.lastSeen()
			if res.StatusCode != 200 || last == nil || last.service != c.service {
				t.Fatalf("status=%d body=%s last=%+v", res.StatusCode, body, last)
			}
			if last.path != c.path || last.query != "x=1" || last.method != c.method {
				t.Fatalf("no se conservó la ruta: %+v", last)
			}
		})
	}
}

func TestUnknownPrefixIsUniform404(t *testing.T) {
	h := newHarness(t)
	for _, path := range []string{"/", "/nada", "/queues", "/authx", "/.well-known/jwks.json"} {
		res, body := h.do(t, "GET", path, h.bearer(allPerms...))
		e := parseError(t, body)
		if res.StatusCode != 404 || e.Code != "NOT_FOUND" || e.RequestID == "" || h.lastSeen() != nil {
			t.Fatalf("%s: %d %s (last=%+v)", path, res.StatusCode, body, h.lastSeen())
		}
	}
}

func TestExternalEnqueueIsBlockedEvenForAdmin(t *testing.T) {
	h := newHarness(t)
	for _, headers := range []map[string]string{
		h.bearer(allPerms...),
		{"X-Service-Token": "lo-que-sea"},
		nil,
	} {
		for _, path := range []string{"/queue/orders", "/queue/orders/"} {
			res, body := h.do(t, "POST", path, headers)
			e := parseError(t, body)
			if res.StatusCode != 403 || e.Code != "FORBIDDEN" || e.RequestID == "" {
				t.Fatalf("%s: %d %s", path, res.StatusCode, body)
			}
			if h.lastSeen() != nil {
				t.Fatalf("la petición bloqueada llegó a %s", h.lastSeen().service)
			}
		}
	}
	if res, _ := h.do(t, "GET", "/queue", h.bearer("kitchen:read")); res.StatusCode != http.StatusOK {
		t.Fatalf("GET /queue = %d", res.StatusCode)
	}
}

func TestUpstreamDownReturnsUniformError(t *testing.T) {
	h := newHarness(t, withConfig(func(c *configAlias) {
		c.OrderURL = mustParse("http://127.0.0.1:1")
	}))
	res, body := h.do(t, "GET", "/menu", h.bearer())
	e := parseError(t, body)
	if (res.StatusCode != 502 && res.StatusCode != 503) || e.Code == "" || e.RequestID == "" || e.Details == nil {
		t.Fatalf("%d %s", res.StatusCode, body)
	}
}
