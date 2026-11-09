package tests

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"sort"
	"strings"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/getkin/kin-openapi/openapi3filter"
	"github.com/getkin/kin-openapi/routers"
	"github.com/getkin/kin-openapi/routers/legacy"
)

func loadSpec(t *testing.T) (*openapi3.T, routers.Router) {
	t.Helper()
	doc, err := openapi3.NewLoader().LoadFromFile("../openapi.yaml")
	if err != nil {
		t.Fatalf("no se pudo cargar openapi.yaml: %v", err)
	}
	if err := doc.Validate(context.Background()); err != nil {
		t.Fatalf("openapi.yaml no es válido: %v", err)
	}
	router, err := legacy.NewRouter(doc)
	if err != nil {
		t.Fatal(err)
	}
	return doc, router
}

func TestOpenAPISpecIsValidAndCoversEveryRoute(t *testing.T) {
	doc, _ := loadSpec(t)

	var got []string
	for path, item := range doc.Paths.Map() {
		for method := range item.Operations() {
			got = append(got, method+" "+path)
		}
	}
	sort.Strings(got)
	want := []string{
		"GET /health", "GET /queue", "GET /queue/orders/{id}", "GET /ready",
		"PATCH /queue/orders/{id}/status", "POST /queue/orders", "POST /queue/orders/{id}/deliver",
	}
	if strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("operaciones documentadas:\n%v\nesperadas:\n%v", got, want)
	}
}

type contract struct {
	t      *testing.T
	e      *env
	router routers.Router
}

func (c contract) call(method, path string, body any, headers map[string]string, wantStatus int, validateRequest bool) []byte {
	c.t.Helper()
	res, raw := c.e.do(c.t, method, path, body, headers)
	if res.StatusCode != wantStatus {
		c.t.Fatalf("%s %s: %d %s, esperado %d", method, path, res.StatusCode, raw, wantStatus)
	}

	vreq, err := http.NewRequest(method, "http://kitchen-service:8001"+path, bytes.NewReader(mustJSON(body)))
	if err != nil {
		c.t.Fatal(err)
	}
	if body != nil {
		vreq.Header.Set("Content-Type", "application/json")
	}
	for k, v := range headers {
		vreq.Header.Set(k, v)
	}
	route, params, err := c.router.FindRoute(vreq)
	if err != nil {
		c.t.Fatalf("%s %s no está en el contrato: %v", method, path, err)
	}
	input := &openapi3filter.RequestValidationInput{
		Request: vreq, PathParams: params, Route: route,
		Options: &openapi3filter.Options{AuthenticationFunc: openapi3filter.NoopAuthenticationFunc},
	}
	if validateRequest {
		if err := openapi3filter.ValidateRequest(context.Background(), input); err != nil {
			c.t.Fatalf("la petición %s %s no cumple el contrato: %v", method, path, err)
		}
	}
	err = openapi3filter.ValidateResponse(context.Background(), &openapi3filter.ResponseValidationInput{
		RequestValidationInput: input,
		Status:                 res.StatusCode,
		Header:                 res.Header,
		Body:                   io.NopCloser(bytes.NewReader(raw)),
		Options:                &openapi3filter.Options{IncludeResponseStatus: true},
	})
	if err != nil {
		c.t.Fatalf("la respuesta %d de %s %s no cumple el contrato: %v\ncuerpo: %s", res.StatusCode, method, path, err, raw)
	}
	return raw
}

func mustJSON(v any) []byte {
	if v == nil {
		return nil
	}
	raw, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return raw
}

func TestRealResponsesMatchTheOpenAPIContract(t *testing.T) {
	_, router := loadSpec(t)
	e := newEnvCap(t, 1)
	c := contract{t: t, e: e, router: router}

	c.call("GET", "/health", nil, nil, 200, true)
	c.call("GET", "/ready", nil, nil, 200, true)

	c.call("POST", "/queue/orders", orderBody(orderA, 8), svcHeaders(), 201, true)
	c.call("POST", "/queue/orders", orderBody(orderA, 8), svcHeaders(), 200, true)
	c.call("POST", "/queue/orders", orderBody(orderB, 4), svcHeaders(), 201, true)
	noItems := orderBody(orderC, 0)
	delete(noItems, "items")
	c.call("POST", "/queue/orders", noItems, svcHeaders(), 201, true)
	c.call("POST", "/queue/orders", map[string]any{"order_id": "x"}, svcHeaders(), 400, false)
	c.call("POST", "/queue/orders", orderBody(orderA, 8), nil, 401, true)

	cook, dispatcher := userHeaders(cookPerms), userHeaders(dispatchPerms)
	c.call("GET", "/queue", nil, cook, 200, true)
	c.call("GET", "/queue?status=pendiente", nil, cook, 200, true)
	c.call("GET", "/queue?status=entregado", nil, cook, 200, true)
	c.call("GET", "/queue?status=cocinando", nil, cook, 400, false)
	c.call("GET", "/queue", nil, nil, 401, true)
	c.call("GET", "/queue", nil, userHeaders("orders:read_own"), 403, true)
	c.call("GET", "/queue", nil, dispatcher, 200, true)
	c.call("GET", "/queue/orders/"+orderA, nil, cook, 200, true)
	c.call("GET", "/queue/orders/"+orderA, nil, dispatcher, 404, true)
	c.call("GET", "/queue/orders/"+orderA, nil, nil, 401, true)
	c.call("GET", "/queue/orders/"+orderA, nil, userHeaders("orders:read_own"), 403, true)
	c.call("GET", "/queue/orders/"+orderC+"0", nil, cook, 404, true)

	path := "/queue/orders/" + orderA + "/status"
	c.call("PATCH", path, map[string]string{"status": "listo"}, cook, 409, true)
	c.call("PATCH", path, map[string]string{"status": "en_preparacion"}, cook, 200, true)
	c.call("PATCH", "/queue/orders/"+orderB+"/status", map[string]string{"status": "en_preparacion"}, cook, 409, true)
	c.call("PATCH", path, map[string]string{"status": "cocinando"}, cook, 400, false)
	c.call("PATCH", path, map[string]string{"status": "listo"}, nil, 401, true)
	c.call("PATCH", path, map[string]string{"status": "listo"}, dispatcher, 403, true)
	c.call("PATCH", "/queue/orders/"+orderC+"0/status", map[string]string{"status": "listo"}, cook, 404, true)
	c.call("PATCH", path, map[string]string{"status": "listo"}, cook, 200, true)

	deliver := "/queue/orders/" + orderA + "/deliver"
	c.call("POST", deliver, nil, nil, 401, true)
	c.call("POST", deliver, nil, cook, 403, true)
	c.call("POST", "/queue/orders/"+orderB+"/deliver", nil, dispatcher, 409, true)
	c.call("POST", deliver, nil, dispatcher, 200, true)
	c.call("POST", deliver, nil, dispatcher, 409, true)
	c.call("POST", "/queue/orders/"+orderC+"0/deliver", nil, dispatcher, 404, true)
	c.call("GET", "/queue/orders/"+orderA, nil, cook, 200, true)
}
