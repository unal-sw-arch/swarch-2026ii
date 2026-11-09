package tests

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"math/big"
	"net/http"
	"net/http/httptest"
	"net/url"
	"sync"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"

	"api-gateway/internal/config"
	"api-gateway/internal/jwks"
	"api-gateway/internal/router"
)

const (
	issuer   = "identity-service"
	audience = "panaderia-express"
	userID   = "8f14e45f-ceea-467f-a0e6-1f3d3b6a1c11"
	otherID  = "9a24e45f-ceea-467f-a0e6-1f3d3b6a1c22"
)

var allPerms = []string{
	"orders:create_own", "orders:read_own", "orders:create", "orders:charge", "orders:read_all",
	"reservations:manage_own", "reservations:manage_all", "kitchen:read", "kitchen:update", "kitchen:deliver",
	"products:manage", "history:read_own", "history:read_all", "reports:read",
	"users:manage", "users:manage_operational", "roles:manage",
}

type seen struct {
	service string
	method  string
	path    string
	query   string
	body    string
	header  http.Header
}

type harness struct {
	gateway *httptest.Server
	jwks    *httptest.Server
	mr      *miniredis.Miniredis
	key     *rsa.PrivateKey
	kid     string

	mu       sync.Mutex
	last     *seen
	pubKeys  map[string]*rsa.PublicKey
	jwksDown bool
}

type options struct {
	config    func(*config.Config)
	cacheOpts []jwks.Option
}

type option func(*options)

func withConfig(f func(*config.Config)) option { return func(o *options) { o.config = f } }
func withCache(opts ...jwks.Option) option     { return func(o *options) { o.cacheOpts = opts } }

func (h *harness) lastSeen() *seen {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.last
}

func (h *harness) setJWKSDown(down bool) {
	h.mu.Lock()
	h.jwksDown = down
	h.mu.Unlock()
}

func (h *harness) addKey(kid string, pub *rsa.PublicKey) {
	h.mu.Lock()
	h.pubKeys[kid] = pub
	h.mu.Unlock()
}

func newKey(t *testing.T) *rsa.PrivateKey {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	return key
}

func (h *harness) serveJWKS(w http.ResponseWriter, _ *http.Request) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.jwksDown {
		http.Error(w, "caído", http.StatusInternalServerError)
		return
	}
	var keys []map[string]string
	for kid, pub := range h.pubKeys {
		keys = append(keys, map[string]string{
			"kty": "RSA", "kid": kid, "use": "sig", "alg": "RS256",
			"n": base64.RawURLEncoding.EncodeToString(pub.N.Bytes()),
			"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(pub.E)).Bytes()),
		})
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{"keys": keys})
}

func (h *harness) fakeService(t *testing.T, name string) *url.URL {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		h.mu.Lock()
		h.last = &seen{
			service: name, method: r.Method, path: r.URL.Path, query: r.URL.RawQuery,
			body: string(body), header: r.Header.Clone(),
		}
		h.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"service":"`+name+`"}`)
	}))
	t.Cleanup(srv.Close)
	u, err := url.Parse(srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	return u
}

func newHarness(t *testing.T, opts ...option) *harness {
	t.Helper()
	var o options
	for _, opt := range opts {
		opt(&o)
	}

	h := &harness{mr: miniredis.RunT(t), key: sharedKey(t), kid: "kid-1", pubKeys: map[string]*rsa.PublicKey{}}
	h.pubKeys[h.kid] = &h.key.PublicKey
	h.jwks = httptest.NewServer(http.HandlerFunc(h.serveJWKS))
	t.Cleanup(h.jwks.Close)

	rdb := redis.NewClient(&redis.Options{Addr: h.mr.Addr()})
	t.Cleanup(func() { rdb.Close() })

	cfg := config.Config{
		Port:                   "8080",
		IdentityURL:            h.fakeService(t, "identity"),
		OrderURL:               h.fakeService(t, "order"),
		KitchenURL:             h.fakeService(t, "kitchen"),
		ReportingURL:           h.fakeService(t, "reporting"),
		JWKSURL:                h.jwks.URL,
		JWTIssuer:              issuer,
		JWTAudience:            audience,
		CORSOrigins:            []string{"http://localhost:3000"},
		RateLimitUserPerMinute: 1000,
		RateLimitIPPerMinute:   1000,
	}
	if o.config != nil {
		o.config(&cfg)
	}

	cacheOpts := append([]jwks.Option{jwks.WithMinRefresh(0)}, o.cacheOpts...)
	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	h.gateway = httptest.NewServer(router.New(cfg, rdb, log, router.WithKeys(jwks.New(h.jwks.URL, cacheOpts...))))
	t.Cleanup(h.gateway.Close)
	return h
}

func (h *harness) claims(perms ...string) jwt.MapClaims {
	return jwt.MapClaims{
		"sub": userID, "email": "cocina@demo.com", "roles": []string{"cocinero"},
		"permissions": perms, "level": 50, "iss": issuer, "aud": audience,
		"iat": time.Now().Unix(), "exp": time.Now().Add(15 * time.Minute).Unix(),
	}
}

func (h *harness) signWith(claims jwt.MapClaims, method jwt.SigningMethod, key any, kid string) string {
	tok := jwt.NewWithClaims(method, claims)
	tok.Header["kid"] = kid
	raw, err := tok.SignedString(key)
	if err != nil {
		panic(err)
	}
	return raw
}

func (h *harness) sign(claims jwt.MapClaims) string {
	return h.signWith(claims, jwt.SigningMethodRS256, h.key, h.kid)
}

func (h *harness) bearer(perms ...string) map[string]string {
	return map[string]string{"Authorization": "Bearer " + h.sign(h.claims(perms...))}
}

func (h *harness) do(t *testing.T, method, path string, headers map[string]string) (*http.Response, string) {
	return h.doBody(t, method, path, "", headers)
}

func (h *harness) doBody(t *testing.T, method, path, body string, headers map[string]string) (*http.Response, string) {
	t.Helper()
	var rd io.Reader
	if body != "" {
		rd = stringsReader(body)
	}
	req, _ := http.NewRequest(method, h.gateway.URL+path, rd)
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

func parseError(t *testing.T, body string) apiError {
	t.Helper()
	var e apiError
	if err := json.Unmarshal([]byte(body), &e); err != nil {
		t.Fatalf("no es el formato de error uniforme: %q", body)
	}
	return e
}

type configAlias = config.Config

func mustParse(raw string) *url.URL {
	u, err := url.Parse(raw)
	if err != nil {
		panic(err)
	}
	return u
}

var (
	keyOnce sync.Once
	keyVal  *rsa.PrivateKey
)

func sharedKey(t *testing.T) *rsa.PrivateKey {
	t.Helper()
	keyOnce.Do(func() { keyVal = newKey(t) })
	return keyVal
}
