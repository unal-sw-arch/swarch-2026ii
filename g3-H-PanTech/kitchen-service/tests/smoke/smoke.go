package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os/exec"
	"strings"
	"time"

	"github.com/google/uuid"
)

func (s *suite) runSmoke() {
	long, short, medium := uuid.NewString(), uuid.NewString(), uuid.NewString()

	s.section("Salud")
	for name, base := range map[string]string{"gateway": s.cfg.gateway, "kitchen": s.cfg.kitchen} {
		r := s.do("GET", base+"/health", nil, nil)
		s.check(name+" GET /health", r.status == 200 && strings.TrimSpace(string(r.body)) == `{"status":"ok"}`, "%d %s", r.status, truncate(r.body))
		r = s.do("GET", base+"/ready", nil, nil)
		s.check(name+" GET /ready", r.status == 200 && strings.TrimSpace(string(r.body)) == `{"status":"ready"}`, "%d %s", r.status, truncate(r.body))
	}

	s.section("Gateway: autenticación y ruteo")
	r := s.gw("GET", "/queue", nil, nil)
	s.status("sin token responde 401 UNAUTHENTICATED", r, 401, "UNAUTHENTICATED")
	s.check("el 401 usa el formato uniforme con request_id", r.uniformError(), "%s", truncate(r.body))
	s.check("el Gateway devuelve X-Request-Id", r.header.Get("X-Request-Id") != "", "falta el encabezado")

	r = s.gw("GET", "/menu", nil, s.as(s.ids.client))
	s.check("GET /menu llega al upstream de Order (503 si no está levantado)",
		r.status != 401 && r.status != 403 && r.status != 404 && (r.status != 503 || r.errCode() == "SERVICE_UNAVAILABLE"),
		"%d %s", r.status, truncate(r.body))
	r = s.gw("POST", "/auth/login", map[string]string{}, nil)
	s.check("POST /auth/login es público y enruta a Identity", r.status != 401 && r.status != 403 && r.status != 404, "%d %s", r.status, truncate(r.body))
	s.status("ruta desconocida responde 404 uniforme", s.gw("GET", "/nada", nil, s.as(s.ids.cook)), 404, "NOT_FOUND")

	s.section("Kitchen: encolar con X-Service-Token")
	s.status("sin X-Service-Token responde 401", s.kt("POST", "/queue/orders", orderBody(long, 12), nil), 401, "UNAUTHENTICATED")
	r = s.enqueue(long, 12)
	var first view
	_ = r.into(&first)
	s.check("pedido largo (12 min) encolado: 201", r.status == 201 && first.Status == "pendiente" && first.PrepMinutes == 12, "%d %s", r.status, truncate(r.body))
	time.Sleep(1100 * time.Millisecond)
	r = s.enqueue(long, 99)
	var again view
	_ = r.into(&again)
	s.check("repetir el mismo order_id es idempotente: 200 sin cambios", r.status == 200 && again.PrepMinutes == 12 && again.EnqueuedAt == first.EnqueuedAt, "%d %s", r.status, truncate(r.body))
	s.status("cuerpo inválido responde 400 VALIDATION_ERROR", s.kt("POST", "/queue/orders", map[string]any{"order_id": "x"}, s.serviceHeaders()), 400, "VALIDATION_ERROR")
	s.check("pedido corto (3 min) encolado", s.enqueue(short, 3).status == 201, "no se pudo encolar")
	s.check("pedido mediano (6 min) encolado", s.enqueue(medium, 6).status == 201, "no se pudo encolar")

	s.section("Cola de cocina a través del Gateway")
	q, r := s.queueAs(s.ids.cook)
	s.check("el cocinero ve la cola", r.status == 200 && len(q.Items) >= 3, "%d %s", r.status, truncate(r.body))
	pShort, pMed, pLong := indexOf(q.Items, short), indexOf(q.Items, medium), indexOf(q.Items, long)
	s.check("SJF: corto, mediano y largo en ese orden", pShort >= 0 && pShort < pMed && pMed < pLong, "posiciones %d %d %d", pShort, pMed, pLong)
	if pShort >= 0 {
		s.check("posición 1 y ETA igual al tiempo del primero", q.Items[pShort].Position == 1 && q.Items[pShort].ETAMinutes == 3, "%+v", q.Items[pShort])
	}
	s.check("la capacidad se refleja en la API", q.Capacity.Max == 4 && q.Capacity.InPreparation == 0, "%+v", q.Capacity)
	dq, r := s.queueAs(s.ids.dispatcher)
	s.check("el despachador no ve pedidos pendientes", r.status == 200 && len(dq.Items) == 0, "%d %s", r.status, truncate(r.body))

	s.section("Estados, entrega y errores")
	s.status("pendiente -> listo responde 409 INVALID_STATE", s.setStatus(s.ids.cook, short, "listo"), 409, "INVALID_STATE")
	r = s.setStatus(s.ids.cook, short, "en_preparacion")
	var v view
	_ = r.into(&v)
	s.check("pendiente -> en_preparacion", r.status == 200 && v.Status == "en_preparacion", "%d %s", r.status, truncate(r.body))
	s.status("repetir en_preparacion responde 409", s.setStatus(s.ids.cook, short, "en_preparacion"), 409, "INVALID_STATE")
	s.status("entregar sin estar listo responde 409", s.deliver(s.ids.dispatcher, short), 409, "INVALID_STATE")
	r = s.setStatus(s.ids.cook, short, "listo")
	_ = r.into(&v)
	s.check("en_preparacion -> listo", r.status == 200 && v.Status == "listo", "%d %s", r.status, truncate(r.body))
	dq, _ = s.queueAs(s.ids.dispatcher)
	s.check("el despachador ve el pedido listo", len(dq.Items) == 1 && dq.Items[0].OrderID == short, "%+v", dq.Items)
	r = s.deliver(s.ids.dispatcher, short)
	_ = r.into(&v)
	s.check("el despachador entrega", r.status == 200 && v.Status == "entregado", "%d %s", r.status, truncate(r.body))
	s.status("doble entrega responde 409", s.deliver(s.ids.dispatcher, short), 409, "INVALID_STATE")
	s.status("pedido inexistente responde 404", s.setStatus(s.ids.cook, uuid.NewString(), "listo"), 404, "NOT_FOUND")
	q, _ = s.queueAs(s.ids.cook)
	s.check("lo entregado sale de la cola activa", indexOf(q.Items, short) == -1, "sigue en la cola")

	s.section("Capacidad de cocina")
	extra := []string{uuid.NewString(), uuid.NewString(), uuid.NewString()}
	for _, id := range extra {
		s.enqueue(id, 5)
	}
	started := []string{medium, long, extra[0], extra[1]}
	allOK := true
	for _, id := range started {
		if r := s.setStatus(s.ids.cook, id, "en_preparacion"); r.status != 200 {
			allOK = false
		}
	}
	s.check("la cocina acepta 4 pedidos en preparación", allOK, "alguno fue rechazado")
	r = s.setStatus(s.ids.cook, extra[2], "en_preparacion")
	var capErr struct {
		Details struct {
			Max           int `json:"max"`
			InPreparation int `json:"in_preparation"`
		} `json:"details"`
	}
	_ = r.into(&capErr)
	s.check("el quinto responde 409 KITCHEN_AT_CAPACITY con max e in_preparation",
		r.status == 409 && r.errCode() == "KITCHEN_AT_CAPACITY" && capErr.Details.Max == 4 && capErr.Details.InPreparation == 4, "%d %s", r.status, truncate(r.body))
	q, _ = s.queueAs(s.ids.cook)
	pos := indexOf(q.Items, extra[2])
	s.check("el pedido rechazado sigue pendiente (no se pierde)", pos >= 0 && q.Items[pos].Status == "pendiente" && q.Capacity.InPreparation == 4, "%+v", q.Capacity)
	s.check("terminar uno libera un cupo", s.setStatus(s.ids.cook, extra[0], "listo").status == 200, "no pasó a listo")
	s.check("con cupo libre el quinto sí inicia", s.setStatus(s.ids.cook, extra[2], "en_preparacion").status == 200, "sigue rechazado")

	s.section("Evento order.ready en RabbitMQ")
	s.checkOrderReady(short)

	s.section("Historial en PostgreSQL")
	s.checkHistory(short)
}

func (s *suite) checkOrderReady(orderID string) {
	api := s.cfg.rabbit + "/api/queues/%2F/worker.order_ready/get"
	headers := map[string]string{"Authorization": "Basic " + basic(s.cfg.rabbitUser, s.cfg.rabbitPass)}
	body := map[string]any{"count": 50, "ackmode": "ack_requeue_true", "encoding": "auto"}

	var msgs []struct {
		Payload string `json:"payload"`
	}
	deadline := time.Now().Add(15 * time.Second)
	found := false
	for time.Now().Before(deadline) && !found {
		r := s.do("POST", api, body, headers)
		if r.status != 200 {
			s.check("consola de RabbitMQ accesible", false, "%d %s", r.status, truncate(r.body))
			return
		}
		_ = r.into(&msgs)
		for _, m := range msgs {
			var env struct {
				EventID    string         `json:"event_id"`
				EventType  string         `json:"event_type"`
				Version    int            `json:"version"`
				OccurredAt string         `json:"occurred_at"`
				Producer   string         `json:"producer"`
				Payload    map[string]any `json:"payload"`
			}
			if json.Unmarshal([]byte(m.Payload), &env) != nil || env.Payload["order_id"] != orderID {
				continue
			}
			found = true
			_, errID := uuid.Parse(env.EventID)
			_, errTime := time.Parse(time.RFC3339, env.OccurredAt)
			s.check("order.ready llegó a worker.order_ready", true, "")
			s.check("sobre: event_type, version, producer, event_id y occurred_at",
				env.EventType == "order.ready" && env.Version == 1 && env.Producer == "kitchen-service" && errID == nil && errTime == nil, "%+v", env)
			_, errReady := time.Parse(time.RFC3339, fmt.Sprint(env.Payload["ready_at"]))
			s.check("payload: order_id, customer_id, ready_at y prep_minutes",
				len(env.Payload) == 4 && env.Payload["customer_id"] != nil && errReady == nil && env.Payload["prep_minutes"] == float64(3), "%v", env.Payload)
		}
		if !found {
			time.Sleep(time.Second)
		}
	}
	if !found {
		s.check("order.ready llegó a worker.order_ready", false, "no apareció el evento de %s", orderID)
	}
}

func basic(user, pass string) string {
	return base64.StdEncoding.EncodeToString([]byte(user + ":" + pass))
}

func (s *suite) checkHistory(orderID string) {
	if s.cfg.pgContainer == "" {
		s.skip("historial de estados", "sin contenedor de PostgreSQL")
		return
	}
	query := fmt.Sprintf("SELECT status || ':' || (changed_by IS NOT NULL) FROM kitchen.order_status_events WHERE order_id = '%s' ORDER BY id", orderID)
	out, err := exec.Command("docker", "exec", s.cfg.pgContainer, "psql", "-U", "panaderia", "-d", "panaderia", "-At", "-c", query).CombinedOutput()
	if err != nil {
		s.skip("historial de estados", "no se pudo consultar PostgreSQL: "+truncate(out))
		return
	}
	got := strings.Fields(string(out))
	want := []string{"pendiente:false", "en_preparacion:true", "listo:true", "entregado:true"}
	s.check("una fila por transición con changed_by (nulo al encolar)", strings.Join(got, ",") == strings.Join(want, ","), "%v", got)
}
