package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type suite struct {
	cfg    config
	key    *rsa.PrivateKey
	kid    string
	client *http.Client
	ids    identities

	passed, failed, skipped int
}

type identity struct {
	id    string
	perms []string
}

type identities struct {
	cook, dispatcher, supervisor, client, waiter identity
	token                                        func(identity) string
}

func newIdentities(s *suite) identities {
	mk := func(perms ...string) identity { return identity{id: uuid.NewString(), perms: perms} }
	return identities{
		cook:       mk("kitchen:read", "kitchen:update"),
		dispatcher: mk("kitchen:deliver"),
		supervisor: mk("kitchen:read", "kitchen:update", "kitchen:deliver", "history:read_all", "reports:read"),
		client:     mk("orders:create_own", "orders:read_own", "reservations:manage_own", "history:read_own"),
		waiter:     mk("orders:create", "orders:charge", "reservations:manage_all"),
		token:      func(i identity) string { return s.sign(s.claims(i), jwt.SigningMethodRS256, s.key, s.kid) },
	}
}

func (s *suite) claims(i identity) jwt.MapClaims {
	return jwt.MapClaims{
		"sub": i.id, "email": "smoke@demo.com", "roles": []string{"prueba"}, "permissions": i.perms, "level": 50,
		"iss": "identity-service", "aud": "panaderia-express",
		"iat": time.Now().Unix(), "exp": time.Now().Add(15 * time.Minute).Unix(),
	}
}

func (s *suite) sign(claims jwt.MapClaims, method jwt.SigningMethod, key any, kid string) string {
	tok := jwt.NewWithClaims(method, claims)
	tok.Header["kid"] = kid
	raw, err := tok.SignedString(key)
	if err != nil {
		panic(err)
	}
	return raw
}

func (s *suite) as(i identity) map[string]string {
	return map[string]string{"Authorization": "Bearer " + s.ids.token(i)}
}

type resp struct {
	status int
	header http.Header
	body   []byte
}

func (r resp) into(v any) error { return json.Unmarshal(r.body, v) }

func (r resp) errCode() string {
	var e struct {
		Code      string `json:"code"`
		RequestID string `json:"request_id"`
	}
	_ = r.into(&e)
	return e.Code
}

func (r resp) uniformError() bool {
	var e struct {
		Code      string         `json:"code"`
		Message   string         `json:"message"`
		RequestID string         `json:"request_id"`
		Details   map[string]any `json:"details"`
	}
	return r.into(&e) == nil && e.Code != "" && e.Message != "" && e.RequestID != "" && e.Details != nil
}

func (s *suite) do(method, url string, body any, headers map[string]string) resp {
	var rd io.Reader
	switch b := body.(type) {
	case nil:
	case []byte:
		rd = bytes.NewReader(b)
	default:
		raw, _ := json.Marshal(b)
		rd = bytes.NewReader(raw)
	}
	req, err := http.NewRequest(method, url, rd)
	if err != nil {
		panic(err)
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	res, err := s.client.Do(req)
	if err != nil {
		return resp{status: 0, body: []byte(err.Error())}
	}
	defer res.Body.Close()
	raw, _ := io.ReadAll(res.Body)
	return resp{status: res.StatusCode, header: res.Header, body: raw}
}

func (s *suite) gw(method, path string, body any, headers map[string]string) resp {
	return s.do(method, s.cfg.gateway+path, body, headers)
}

func (s *suite) kt(method, path string, body any, headers map[string]string) resp {
	return s.do(method, s.cfg.kitchen+path, body, headers)
}

func merge(maps ...map[string]string) map[string]string {
	out := map[string]string{}
	for _, m := range maps {
		for k, v := range m {
			out[k] = v
		}
	}
	return out
}

func (s *suite) section(title string) { fmt.Printf("\n== %s\n", title) }

func (s *suite) check(name string, ok bool, format string, args ...any) {
	if ok {
		s.passed++
		fmt.Printf("PASS  %s\n", name)
		return
	}
	s.failed++
	fmt.Printf("FAIL  %s: %s\n", name, fmt.Sprintf(format, args...))
}

func (s *suite) skip(name, why string) {
	s.skipped++
	fmt.Printf("SKIP  %s (%s)\n", name, why)
}

func (s *suite) status(name string, got resp, want int, code string) {
	s.check(name, got.status == want && (code == "" || got.errCode() == code),
		"esperado %d %s, obtenido %d %s", want, code, got.status, truncate(got.body))
}

func truncate(b []byte) string {
	s := strings.TrimSpace(string(b))
	if len(s) > 160 {
		return s[:160] + "…"
	}
	return s
}

// waitReady espera a que el Gateway y Kitchen respondan /ready y a que el Gateway acepte el JWKS falso.
func (s *suite) waitReady(ctx context.Context) error {
	for _, base := range []string{s.cfg.kitchen, s.cfg.gateway} {
		for {
			if r := s.do("GET", base+"/ready", nil, nil); r.status == 200 {
				break
			}
			select {
			case <-ctx.Done():
				return fmt.Errorf("%s/ready no respondió 200", base)
			case <-time.After(time.Second):
			}
		}
	}
	// El Gateway renueva el JWKS como máximo cada 10 s ante un kid nuevo.
	for {
		if r := s.gw("GET", "/queue", nil, s.as(s.ids.cook)); r.status == 200 {
			return nil
		} else if r.status != 401 && r.status != 503 {
			return fmt.Errorf("GET /queue con un token válido devolvió %d: %s", r.status, truncate(r.body))
		}
		select {
		case <-ctx.Done():
			return fmt.Errorf("el Gateway no acepta el JWKS en %s (¿JWKS_URL apunta a este proceso?)", s.cfg.jwksAddr)
		case <-time.After(2 * time.Second):
		}
	}
}

func orderBody(id string, prep int, items ...map[string]any) map[string]any {
	if len(items) == 0 {
		items = []map[string]any{{"product_id": uuid.NewString(), "name": "Croissant", "quantity": 2, "prep_minutes": prep}}
	}
	return map[string]any{
		"order_id": id, "customer_id": uuid.NewString(), "order_type": "para_llevar",
		"paid_at": time.Now().UTC().Format(time.RFC3339), "items": items,
	}
}

func (s *suite) serviceHeaders() map[string]string {
	return map[string]string{"X-Service-Token": s.cfg.serviceToken}
}

type view struct {
	OrderID       string  `json:"order_id"`
	Status        string  `json:"status"`
	PrepMinutes   int     `json:"prep_minutes"`
	PriorityScore float64 `json:"priority_score"`
	Position      int     `json:"position"`
	ETAMinutes    int     `json:"eta_minutes"`
	EnqueuedAt    string  `json:"enqueued_at"`
}

type queueResp struct {
	Items    []view `json:"items"`
	Capacity struct {
		Max           int `json:"max"`
		InPreparation int `json:"in_preparation"`
	} `json:"capacity"`
}

func (s *suite) queueAs(i identity) (queueResp, resp) {
	r := s.gw("GET", "/queue", nil, s.as(i))
	var q queueResp
	_ = r.into(&q)
	return q, r
}

func (s *suite) enqueue(id string, prep int) resp {
	return s.kt("POST", "/queue/orders", orderBody(id, prep), s.serviceHeaders())
}

func (s *suite) setStatus(i identity, id, status string) resp {
	return s.gw("PATCH", "/queue/orders/"+id+"/status", map[string]string{"status": status}, s.as(i))
}

func (s *suite) deliver(i identity, id string) resp {
	return s.gw("POST", "/queue/orders/"+id+"/deliver", nil, s.as(i))
}

func indexOf(items []view, id string) int {
	for i, it := range items {
		if it.OrderID == id {
			return i
		}
	}
	return -1
}

func newForeignKey() (*rsa.PrivateKey, error) { return rsa.GenerateKey(rand.Reader, 2048) }
