package main

import (
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// runSecurity ejecuta las pruebas de denegación (A4.2): tokens ajenos o inválidos, rol
// insuficiente, llamadas directas a Kitchen sin X-Service-Token y falsificación de encabezados.
func (s *suite) runSecurity() {
	pending := uuid.NewString()
	s.enqueue(pending, 7)

	s.section("Token ajeno o inválido (401)")
	foreign, err := newForeignKey()
	if err != nil {
		panic(err)
	}
	c := func(f func(m jwt.MapClaims)) string {
		m := s.claims(s.ids.cook)
		f(m)
		return s.sign(m, jwt.SigningMethodRS256, s.key, s.kid)
	}
	tokens := map[string]string{
		"firmado con una clave ajena":      s.sign(s.claims(s.ids.cook), jwt.SigningMethodRS256, foreign, s.kid),
		"clave ajena y kid desconocido":    s.sign(s.claims(s.ids.cook), jwt.SigningMethodRS256, foreign, "otro-kid"),
		"emisor (iss) ajeno":               c(func(m jwt.MapClaims) { m["iss"] = "otro-emisor" }),
		"audiencia (aud) ajena":            c(func(m jwt.MapClaims) { m["aud"] = "otra-app" }),
		"vencido":                          c(func(m jwt.MapClaims) { m["exp"] = time.Now().Add(-time.Hour).Unix() }),
		"sin exp":                          c(func(m jwt.MapClaims) { delete(m, "exp") }),
		"sub que no es UUID":               c(func(m jwt.MapClaims) { m["sub"] = "admin" }),
		"HS256 con secreto inventado":      s.sign(s.claims(s.ids.cook), jwt.SigningMethodHS256, []byte("secreto"), s.kid),
		"alg none":                         s.sign(s.claims(s.ids.cook), jwt.SigningMethodNone, jwt.UnsafeAllowNoneSignatureType, s.kid),
		"permiso con coma (inyección CSV)": c(func(m jwt.MapClaims) { m["permissions"] = []string{"kitchen:read,kitchen:deliver"} }),
	}
	for name, tok := range tokens {
		r := s.gw("GET", "/queue", nil, map[string]string{"Authorization": "Bearer " + tok})
		s.status("token "+name, r, 401, "UNAUTHENTICATED")
	}
	s.status("esquema Basic en lugar de Bearer", s.gw("GET", "/queue", nil, map[string]string{"Authorization": "Basic " + basic("a", "b")}), 401, "UNAUTHENTICATED")
	s.status("token en un encabezado que no es Authorization", s.gw("GET", "/queue", nil, map[string]string{"X-Access-Token": s.ids.token(s.ids.cook)}), 401, "UNAUTHENTICATED")

	s.section("Rol insuficiente (403)")
	someID := pending
	cases := []struct {
		who          string
		id           identity
		method, path string
		body         any
	}{
		{"cliente ve la cola", s.ids.client, "GET", "/queue", nil},
		{"cliente ve el detalle", s.ids.client, "GET", "/queue/orders/" + someID, nil},
		{"cliente cambia estados", s.ids.client, "PATCH", "/queue/orders/" + someID + "/status", map[string]string{"status": "en_preparacion"}},
		{"cliente entrega", s.ids.client, "POST", "/queue/orders/" + someID + "/deliver", nil},
		{"cliente ve reportes", s.ids.client, "GET", "/reports/sales", nil},
		{"cliente administra usuarios", s.ids.client, "GET", "/users", nil},
		{"mesero ve la cola", s.ids.waiter, "GET", "/queue", nil},
		{"mesero cambia estados", s.ids.waiter, "PATCH", "/queue/orders/" + someID + "/status", map[string]string{"status": "en_preparacion"}},
		{"cocinero entrega", s.ids.cook, "POST", "/queue/orders/" + someID + "/deliver", nil},
		{"cocinero ve historial general", s.ids.cook, "GET", "/history", nil},
		{"cocinero ve reportes", s.ids.cook, "GET", "/reports/sales", nil},
		{"despachador cambia estados", s.ids.dispatcher, "PATCH", "/queue/orders/" + someID + "/status", map[string]string{"status": "en_preparacion"}},
		{"despachador ve reportes", s.ids.dispatcher, "GET", "/reports/sales", nil},
		{"supervisor administra roles", s.ids.supervisor, "GET", "/permissions", nil},
		{"supervisor escribe el catálogo", s.ids.supervisor, "POST", "/products", map[string]string{}},
	}
	for _, c := range cases {
		s.status(c.who, s.gw(c.method, c.path, c.body, s.as(c.id)), 403, "FORBIDDEN")
	}
	r := s.gw("GET", "/queue/orders/"+pending, nil, s.as(s.ids.dispatcher))
	s.status("recurso ajeno: el despachador pide un pedido que no está listo (404, no 403)", r, 404, "NOT_FOUND")
	s.status("id mal formado responde 404", s.gw("GET", "/queue/orders/no-es-uuid", nil, s.as(s.ids.cook)), 404, "NOT_FOUND")
	q, _ := s.queueAs(s.ids.cook)
	pos := indexOf(q.Items, pending)
	s.check("las peticiones denegadas no cambiaron el pedido", pos >= 0 && q.Items[pos].Status == "pendiente", "%+v", q.Items)

	s.section("Kitchen directo sin X-Service-Token")
	enqueue := orderBody(uuid.NewString(), 5)
	s.status("POST /queue/orders sin encabezados", s.kt("POST", "/queue/orders", enqueue, nil), 401, "UNAUTHENTICATED")
	s.status("con un X-Service-Token incorrecto", s.kt("POST", "/queue/orders", enqueue, map[string]string{"X-Service-Token": "incorrecto"}), 401, "UNAUTHENTICATED")
	s.status("con un JWT en lugar del token de servicio", s.kt("POST", "/queue/orders", enqueue, s.as(s.ids.supervisor)), 401, "UNAUTHENTICATED")
	s.status("con X-User-Id y todos los permisos (no es token de servicio)", s.kt("POST", "/queue/orders", enqueue,
		map[string]string{"X-User-Id": uuid.NewString(), "X-Permissions": "kitchen:read,kitchen:update,kitchen:deliver"}), 401, "UNAUTHENTICATED")
	s.status("el token de servicio no da acceso a GET /queue (falta X-User-Id)", s.kt("GET", "/queue", nil, s.serviceHeaders()), 401, "UNAUTHENTICATED")
	s.status("el token de servicio no da acceso a PATCH (falta X-User-Id)", s.kt("PATCH", "/queue/orders/"+pending+"/status", map[string]string{"status": "en_preparacion"}, s.serviceHeaders()), 401, "UNAUTHENTICATED")
	s.status("GET /queue sin encabezados", s.kt("GET", "/queue", nil, nil), 401, "UNAUTHENTICATED")
	s.status("identidad sin permisos de cocina", s.kt("GET", "/queue", nil, map[string]string{"X-User-Id": uuid.NewString(), "X-Permissions": "orders:read_own"}), 403, "FORBIDDEN")
	s.status("cuerpo de más de 1 MB", s.kt("POST", "/queue/orders", []byte(`{"order_id":"`+strings.Repeat("a", 2<<20)+`"}`), s.serviceHeaders()), 400, "VALIDATION_ERROR")
	s.status("order_id con caracteres de inyección", s.kt("POST", "/queue/orders", orderBody("'; DROP TABLE kitchen.order_status_events;--", 5), s.serviceHeaders()), 400, "VALIDATION_ERROR")

	s.section("Kitchen confía en los encabezados X-* (por diseño)")
	r = s.kt("GET", "/queue", nil, map[string]string{"X-User-Id": uuid.NewString(), "X-Permissions": "kitchen:read"})
	s.check("directo a Kitchen, X-Permissions se acepta: la frontera es la red interna (Kitchen no se publica en el compose)", r.status == 200, "%d", r.status)

	s.section("Falsificación de encabezados a través del Gateway")
	spoof := map[string]string{
		"X-User-Id": uuid.NewString(), "X-User-Email": "admin@evil.com", "X-Roles": "admin",
		"X-Permissions": "kitchen:read,kitchen:update,kitchen:deliver,users:manage", "X-Level": "100",
		"X-Service-Token": s.cfg.serviceToken,
	}
	withSpoof := func(id identity) map[string]string { return merge(s.as(id), spoof) }
	s.status("cliente + X-Permissions de cocina falsificado: GET /queue", s.gw("GET", "/queue", nil, withSpoof(s.ids.client)), 403, "FORBIDDEN")
	s.status("cliente + X-Permissions falsificado: PATCH", s.gw("PATCH", "/queue/orders/"+pending+"/status", map[string]string{"status": "en_preparacion"}, withSpoof(s.ids.client)), 403, "FORBIDDEN")
	s.status("cliente + X-Permissions falsificado: deliver", s.gw("POST", "/queue/orders/"+pending+"/deliver", nil, withSpoof(s.ids.client)), 403, "FORBIDDEN")
	s.status("cliente + X-Permissions falsificado: /users", s.gw("GET", "/users", nil, withSpoof(s.ids.client)), 403, "FORBIDDEN")
	q, r = s.queueAs(s.ids.dispatcher)
	rs := s.gw("GET", "/queue", nil, withSpoof(s.ids.dispatcher))
	var sq queueResp
	_ = rs.into(&sq)
	s.check("despachador + X-Permissions: kitchen:read falsificado sigue sin ver pendientes", rs.status == 200 && indexOf(sq.Items, pending) == -1, "%d %s", rs.status, truncate(rs.body))
	s.status("sin token, los X-* falsificados no sustituyen al JWT", s.gw("GET", "/queue", nil, spoof), 401, "UNAUTHENTICATED")
	s.status("X-Service-Token falsificado no abre POST /queue/orders por el Gateway", s.gw("POST", "/queue/orders", enqueue, merge(s.as(s.ids.supervisor), s.serviceHeaders())), 403, "FORBIDDEN")
	s.status("POST /queue/orders por el Gateway sin token también se bloquea", s.gw("POST", "/queue/orders", enqueue, s.serviceHeaders()), 403, "FORBIDDEN")
	s.status("el JWKS de Identity no se expone", s.gw("GET", "/.well-known/jwks.json", nil, s.as(s.ids.supervisor)), 404, "NOT_FOUND")
	s.status("X-Request-Id del cliente se reemplaza (no se refleja)", func() resp {
		r := s.gw("GET", "/queue", nil, merge(s.as(s.ids.cook), map[string]string{"X-Request-Id": "id-falso"}))
		if r.header.Get("X-Request-Id") == "id-falso" {
			r.status = -1
		}
		return r
	}(), 200, "")

	s.section("Rate limiting (429)")
	heavy := identity{id: uuid.NewString(), perms: []string{"kitchen:read"}}
	limited, retry := 0, ""
	for i := 0; i < s.cfg.rateLimit+15; i++ {
		r := s.gw("GET", "/queue", nil, s.as(heavy))
		if r.status == 429 {
			if limited == 0 {
				s.check("el 429 usa el formato uniforme", r.errCode() == "RATE_LIMITED" && r.uniformError(), "%s", truncate(r.body))
			}
			limited++
			retry = r.header.Get("Retry-After")
		}
	}
	s.check("superar el límite por usuario devuelve 429 con Retry-After", limited >= 10 && retry != "", "429 recibidos: %d, Retry-After=%q", limited, retry)
	s.status("otro usuario no se ve afectado", s.gw("GET", "/queue", nil, s.as(s.ids.supervisor)), 200, "")
	s.status("/health no tiene rate limiting", s.gw("GET", "/health", nil, nil), 200, "")

	s.section("CORS")
	pre := s.gw("OPTIONS", "/queue", nil, map[string]string{"Origin": "http://evil.example", "Access-Control-Request-Method": "GET"})
	s.check("preflight de un origen no permitido no recibe cabeceras CORS", pre.status == 403 && pre.header.Get("Access-Control-Allow-Origin") == "", "%d %v", pre.status, pre.header)
}
