package tests

import (
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"api-gateway/internal/jwks"
)

func expectError(t *testing.T, status int, code string, got int, body string) {
	t.Helper()
	e := parseError(t, body)
	if got != status || e.Code != code || e.RequestID == "" || e.Details == nil {
		t.Fatalf("esperado %d %s, obtenido %d %s", status, code, got, body)
	}
}

func TestNoTokenIs401OnProtectedRoutes(t *testing.T) {
	h := newHarness(t)
	for _, path := range []string{"/menu", "/orders", "/queue", "/history", "/reports/sales", "/auth/me", "/users", "/products"} {
		res, body := h.do(t, "GET", path, nil)
		expectError(t, 401, "UNAUTHENTICATED", res.StatusCode, body)
		if res.Header.Get("WWW-Authenticate") != "Bearer" {
			t.Fatalf("%s: falta WWW-Authenticate", path)
		}
	}
	if h.lastSeen() != nil {
		t.Fatal("una petición sin token llegó a un servicio")
	}
}

func TestInvalidTokensAre401(t *testing.T) {
	h := newHarness(t)
	otherKey := newKey(t)

	mod := func(f func(c map[string]any)) string {
		c := h.claims("kitchen:read")
		f(c)
		return h.sign(c)
	}
	hs256 := h.signWith(h.claims("kitchen:read"), jwt.SigningMethodHS256, []byte("secreto"), h.kid)
	none := h.signWith(h.claims("kitchen:read"), jwt.SigningMethodNone, jwt.UnsafeAllowNoneSignatureType, h.kid)

	cases := map[string]string{
		"basura":                 "Bearer no-es-un-jwt",
		"esquema Basic":          "Basic " + h.sign(h.claims("kitchen:read")),
		"vencido":                "Bearer " + mod(func(c map[string]any) { c["exp"] = time.Now().Add(-time.Hour).Unix() }),
		"sin exp":                "Bearer " + mod(func(c map[string]any) { delete(c, "exp") }),
		"iss incorrecto":         "Bearer " + mod(func(c map[string]any) { c["iss"] = "otro" }),
		"aud incorrecta":         "Bearer " + mod(func(c map[string]any) { c["aud"] = "otra" }),
		"sub no es UUID":         "Bearer " + mod(func(c map[string]any) { c["sub"] = "admin" }),
		"firmado con otra clave": "Bearer " + h.signWith(h.claims("kitchen:read"), jwt.SigningMethodRS256, otherKey, h.kid),
		"HS256":                  "Bearer " + hs256,
		"alg none":               "Bearer " + none,
		"permiso con coma":       "Bearer " + mod(func(c map[string]any) { c["permissions"] = []string{"kitchen:read,users:manage"} }),
	}
	for name, authz := range cases {
		t.Run(name, func(t *testing.T) {
			res, body := h.do(t, "GET", "/queue", map[string]string{"Authorization": authz})
			expectError(t, 401, "UNAUTHENTICATED", res.StatusCode, body)
		})
	}
	if h.lastSeen() != nil {
		t.Fatal("un token inválido llegó a un servicio")
	}
}

func TestPublicAuthRoutesNeedNoToken(t *testing.T) {
	h := newHarness(t)
	for _, path := range []string{"/auth/login", "/auth/register", "/auth/refresh"} {
		res, body := h.do(t, "POST", path, nil)
		if res.StatusCode != 200 || h.lastSeen().service != "identity" {
			t.Fatalf("%s: %d %s", path, res.StatusCode, body)
		}
	}
	res, body := h.do(t, "POST", "/auth/logout", nil)
	expectError(t, 401, "UNAUTHENTICATED", res.StatusCode, body)
}

func TestIdentityIsPropagatedFromJWT(t *testing.T) {
	h := newHarness(t)
	headers := h.bearer("kitchen:read", "kitchen:update")
	// El cliente intenta hacerse pasar por otra persona con más permisos.
	headers["X-User-Id"] = otherID
	headers["X-User-Email"] = "admin@evil.com"
	headers["X-Roles"] = "admin"
	headers["X-Permissions"] = "users:manage,kitchen:deliver"
	headers["X-Level"] = "100"
	headers["X-Service-Token"] = "secreto-robado"
	headers["X-Request-Id"] = "id-falso"

	res, body := h.do(t, "GET", "/queue", headers)
	last := h.lastSeen()
	if res.StatusCode != 200 || last == nil {
		t.Fatalf("%d %s", res.StatusCode, body)
	}
	want := map[string]string{
		"X-User-Id":     userID,
		"X-User-Email":  "axel@demo.com",
		"X-Roles":       "cocinero",
		"X-Permissions": "kitchen:read,kitchen:update",
		"X-Level":       "50",
	}
	for name, v := range want {
		if got := last.header.Get(name); got != v {
			t.Fatalf("%s = %q, esperado %q", name, got, v)
		}
	}
	if last.header.Get("X-Service-Token") != "" {
		t.Fatal("X-Service-Token del cliente llegó al servicio")
	}
	if id := last.header.Get("X-Request-Id"); id == "" || id == "id-falso" || id != res.Header.Get("X-Request-Id") {
		t.Fatalf("X-Request-Id = %q", id)
	}
	if !strings.HasPrefix(last.header.Get("Authorization"), "Bearer ") {
		t.Fatal("Authorization debe conservarse para Identity")
	}
}

func TestSpoofedHeadersWithoutTokenOnPublicRoute(t *testing.T) {
	h := newHarness(t)
	res, _ := h.do(t, "POST", "/auth/login", map[string]string{
		"X-User-Id": otherID, "X-Permissions": "users:manage", "X-Service-Token": "robado",
	})
	last := h.lastSeen()
	if res.StatusCode != 200 || last.header.Get("X-User-Id") != "" || last.header.Get("X-Permissions") != "" || last.header.Get("X-Service-Token") != "" {
		t.Fatalf("los encabezados falsificados llegaron: %+v", last.header)
	}
}

func TestCustomerIDIsNeverRewritten(t *testing.T) {
	h := newHarness(t)
	body := `{"customer_id":"` + otherID + `","items":[]}`
	res, _ := h.doBody(t, "POST", "/orders", body, h.bearer("orders:create_own"))
	if res.StatusCode != 200 || h.lastSeen().body != body {
		t.Fatalf("el cuerpo debe viajar intacto: %q", h.lastSeen().body)
	}
	if h.lastSeen().header.Get("X-User-Id") != userID {
		t.Fatal("la identidad debe salir del JWT")
	}
}

func TestPermissionMatrix(t *testing.T) {
	client := []string{"orders:create_own", "orders:read_own", "reservations:manage_own", "history:read_own"}
	cook := []string{"kitchen:read", "kitchen:update"}
	dispatcher := []string{"kitchen:deliver"}
	waiter := []string{"orders:create", "orders:charge", "reservations:manage_all"}
	supervisor := []string{"history:read_all", "reports:read", "users:manage_operational", "kitchen:read", "kitchen:update", "kitchen:deliver"}

	cases := []struct {
		name   string
		perms  []string
		method string
		path   string
		want   int
	}{
		{"cliente ve el menú", client, "GET", "/menu", 200},
		{"cliente crea pedido", client, "POST", "/orders", 200},
		{"cliente ve su historial", client, "GET", "/history", 200},
		{"cliente no ve la cola", client, "GET", "/queue", 403},
		{"cliente no cambia estados", client, "PATCH", "/queue/orders/1/status", 403},
		{"cliente no entrega", client, "POST", "/queue/orders/1/deliver", 403},
		{"cliente no ve reportes", client, "GET", "/reports/sales", 403},
		{"cliente no gestiona usuarios", client, "GET", "/users", 403},
		{"cliente no ve auditoría", client, "GET", "/audit", 403},
		{"cliente lee el catálogo", client, "GET", "/products", 200},
		{"cliente no escribe el catálogo", client, "POST", "/products", 403},
		{"cocinero ve la cola", cook, "GET", "/queue", 200},
		{"cocinero cambia estado", cook, "PATCH", "/queue/orders/1/status", 200},
		{"cocinero no cobra", cook, "POST", "/orders/1/payment", 403},
		{"cocinero no ve historial", cook, "GET", "/history", 403},
		{"despachador ve la cola", dispatcher, "GET", "/queue", 200},
		{"despachador entrega", dispatcher, "POST", "/queue/orders/1/deliver", 200},
		{"mesero cobra", waiter, "POST", "/orders/1/payment", 200},
		{"mesero gestiona reservas", waiter, "GET", "/reservations", 200},
		{"mesero no ve la cola", waiter, "GET", "/queue", 403},
		{"supervisor ve reportes", supervisor, "GET", "/reports/sales", 200},
		{"supervisor gestiona cuentas operativas", supervisor, "GET", "/users", 200},
		{"supervisor no gestiona roles", supervisor, "GET", "/permissions", 403},
		{"admin gestiona roles", []string{"roles:manage"}, "GET", "/permissions", 200},
		{"admin escribe catálogo", []string{"products:manage"}, "DELETE", "/products/1", 200},
		{"sin permisos no entra a pedidos", nil, "GET", "/orders", 403},
		{"autenticado sin permisos ve el menú", nil, "GET", "/menu", 200},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			h := newHarness(t)
			res, body := h.do(t, c.method, c.path, h.bearer(c.perms...))
			if res.StatusCode != c.want {
				t.Fatalf("%s %s = %d %s, esperado %d", c.method, c.path, res.StatusCode, body, c.want)
			}
			if c.want == 403 {
				expectError(t, 403, "FORBIDDEN", res.StatusCode, body)
				if h.lastSeen() != nil {
					t.Fatal("una petición denegada llegó al servicio")
				}
			}
		})
	}
}

func TestRateLimitByIP(t *testing.T) {
	h := newHarness(t, withConfig(func(c *configAlias) { c.RateLimitIPPerMinute = 3 }))
	for i := 1; i <= 3; i++ {
		res, body := h.do(t, "POST", "/auth/login", nil)
		if res.StatusCode != 200 {
			t.Fatalf("petición %d = %d %s", i, res.StatusCode, body)
		}
	}
	res, body := h.do(t, "POST", "/auth/login", nil)
	expectError(t, 429, "RATE_LIMITED", res.StatusCode, body)
	if res.Header.Get("Retry-After") == "" || res.Header.Get("X-RateLimit-Limit") != "3" {
		t.Fatalf("faltan encabezados de límite: %v", res.Header)
	}
	if res, _ := h.do(t, "GET", "/health", nil); res.StatusCode != 200 {
		t.Fatal("/health no debe tener rate limiting")
	}
}

func TestRateLimitByUser(t *testing.T) {
	h := newHarness(t, withConfig(func(c *configAlias) { c.RateLimitUserPerMinute = 2 }))
	mine := h.bearer("kitchen:read")
	for i := 1; i <= 2; i++ {
		if res, body := h.do(t, "GET", "/queue", mine); res.StatusCode != 200 {
			t.Fatalf("petición %d = %d %s", i, res.StatusCode, body)
		}
	}
	res, body := h.do(t, "GET", "/queue", mine)
	expectError(t, 429, "RATE_LIMITED", res.StatusCode, body)

	other := h.claims("kitchen:read")
	other["sub"] = otherID
	if res, body := h.do(t, "GET", "/queue", map[string]string{"Authorization": "Bearer " + h.sign(other)}); res.StatusCode != 200 {
		t.Fatalf("otro usuario no debe verse afectado: %d %s", res.StatusCode, body)
	}
}

func TestRateLimitFailsOpenWithoutRedis(t *testing.T) {
	h := newHarness(t, withConfig(func(c *configAlias) { c.RateLimitUserPerMinute = 1 }))
	h.mr.Close()
	for i := 0; i < 3; i++ {
		if res, body := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
			t.Fatalf("sin Redis el tráfico debe pasar: %d %s", res.StatusCode, body)
		}
	}
}

func TestJWKSUnavailableIs503ThenRecovers(t *testing.T) {
	h := newHarness(t)
	h.setJWKSDown(true)
	res, body := h.do(t, "GET", "/menu", h.bearer())
	expectError(t, 503, "SERVICE_UNAVAILABLE", res.StatusCode, body)

	h.setJWKSDown(false)
	if res, body := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
		t.Fatalf("tras recuperarse Identity: %d %s", res.StatusCode, body)
	}
}

func TestJWKSKeyRotationRefreshesOnUnknownKid(t *testing.T) {
	h := newHarness(t)
	if res, _ := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
		t.Fatal("la clave inicial debe funcionar")
	}

	rotated := newKey(t)
	token := h.signWith(h.claims(), jwt.SigningMethodRS256, rotated, "kid-2")
	headers := map[string]string{"Authorization": "Bearer " + token}

	res, body := h.do(t, "GET", "/menu", headers)
	expectError(t, 401, "UNAUTHENTICATED", res.StatusCode, body)

	h.addKey("kid-2", &rotated.PublicKey)
	if res, body := h.do(t, "GET", "/menu", headers); res.StatusCode != 200 {
		t.Fatalf("con la clave nueva publicada: %d %s", res.StatusCode, body)
	}
	if res, _ := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
		t.Fatal("la clave anterior sigue publicada y debe seguir valiendo")
	}
}

func TestJWKSStaleKeyIsUsedWhileIdentityIsDown(t *testing.T) {
	h := newHarness(t, withCache(jwks.WithTTL(time.Millisecond)))
	if res, _ := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
		t.Fatal("la clave inicial debe funcionar")
	}
	h.setJWKSDown(true)
	time.Sleep(10 * time.Millisecond)
	if res, body := h.do(t, "GET", "/menu", h.bearer()); res.StatusCode != 200 {
		t.Fatalf("debe usar la clave en caché: %d %s", res.StatusCode, body)
	}
}

func TestCORS(t *testing.T) {
	h := newHarness(t)
	allowed := "http://localhost:3000"

	res, _ := h.do(t, "OPTIONS", "/orders", map[string]string{
		"Origin": allowed, "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "authorization",
	})
	if res.StatusCode != 204 || res.Header.Get("Access-Control-Allow-Origin") != allowed ||
		!strings.Contains(res.Header.Get("Access-Control-Allow-Headers"), "Authorization") {
		t.Fatalf("preflight permitido: %d %v", res.StatusCode, res.Header)
	}
	if h.lastSeen() != nil {
		t.Fatal("el preflight no debe llegar a los servicios")
	}

	res, body := h.do(t, "OPTIONS", "/orders", map[string]string{"Origin": "http://evil.com", "Access-Control-Request-Method": "POST"})
	expectError(t, 403, "FORBIDDEN", res.StatusCode, body)
	if res.Header.Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("origen no permitido no debe recibir cabeceras CORS")
	}

	headers := h.bearer("orders:read_own")
	headers["Origin"] = allowed
	res, _ = h.do(t, "GET", "/orders", headers)
	if res.StatusCode != 200 || res.Header.Get("Access-Control-Allow-Origin") != allowed || res.Header.Get("Vary") != "Origin" {
		t.Fatalf("petición normal con origen permitido: %d %v", res.StatusCode, res.Header)
	}

	headers["Origin"] = "http://evil.com"
	res, _ = h.do(t, "GET", "/orders", headers)
	if res.Header.Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("origen no permitido no debe recibir cabeceras CORS")
	}
}
