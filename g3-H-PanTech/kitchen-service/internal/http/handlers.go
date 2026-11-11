package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"kitchen-service/internal/queue"
)

const maxBodyBytes = 1 << 20

type Handler struct {
	Svc          *queue.Service
	ServiceToken string
	Log          *slog.Logger
	Ready        map[string]func(ctx context.Context) error
}

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *Handler) ReadyCheck(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	deps := map[string]string{}
	ok := true
	names := make([]string, 0, len(h.Ready))
	for name := range h.Ready {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		if err := h.Ready[name](ctx); err != nil {
			deps[name] = "down"
			ok = false
			h.Log.Warn("dependencia no disponible", "dependency", name, "error", err.Error())
		} else {
			deps[name] = "up"
		}
	}
	if !ok {
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{"status": "not_ready", "dependencies": deps})
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ready"})
}

type enqueueItemRequest struct {
	ProductID   string `json:"product_id"`
	Name        string `json:"name"`
	Quantity    int    `json:"quantity"`
	PrepMinutes *int   `json:"prep_minutes"`
}

type enqueueRequest struct {
	OrderID    string               `json:"order_id"`
	CustomerID string               `json:"customer_id"`
	OrderType  string               `json:"order_type"`
	PaidAt     string               `json:"paid_at"`
	Items      []enqueueItemRequest `json:"items"`
}

func (req enqueueRequest) validate() (queue.EnqueueInput, map[string]any) {
	problems := map[string]any{}
	in := queue.EnqueueInput{OrderID: req.OrderID, CustomerID: req.CustomerID, OrderType: req.OrderType}

	if _, err := uuid.Parse(req.OrderID); err != nil {
		problems["order_id"] = "debe ser un UUID"
	}
	// `customer_id` es opcional: los pedidos de mostrador creados por el
	// personal no tienen cliente asociado (Order Service envía null).
	if strings.TrimSpace(req.CustomerID) == "" {
		req.CustomerID = ""
	}
	if req.OrderType != queue.OrderTypeParaLlevar && req.OrderType != queue.OrderTypeEnMesa {
		problems["order_type"] = "debe ser para_llevar o en_mesa"
	}
	paidAt, err := time.Parse(time.RFC3339, req.PaidAt)
	if err != nil {
		problems["paid_at"] = "debe ser una fecha ISO-8601"
	}
	in.PaidAt = paidAt

	for i, it := range req.Items {
		prefix := "items[" + strconv.Itoa(i) + "]."
		// Order Service usa slugs (`p-cafe-americano`), no UUID, como id de
		// producto. Solo exigimos que venga informado.
		if strings.TrimSpace(it.ProductID) == "" {
			problems[prefix+"product_id"] = "no puede estar vacío"
		}
		if it.Quantity < 1 {
			problems[prefix+"quantity"] = "debe ser >= 1"
		}
		prep := 0
		if it.PrepMinutes != nil {
			if *it.PrepMinutes < 0 {
				problems[prefix+"prep_minutes"] = "no puede ser negativo"
			}
			prep = *it.PrepMinutes
		}
		in.Items = append(in.Items, queue.OrderItem{
			ProductID: it.ProductID, Name: it.Name, Quantity: it.Quantity, PrepMinutes: prep,
		})
	}
	return in, problems
}

func (h *Handler) EnqueueOrder(w http.ResponseWriter, r *http.Request) {
	if !validServiceToken(r, h.ServiceToken) {
		writeError(w, r, http.StatusUnauthorized, CodeUnauthenticated, "X-Service-Token ausente o inválido", nil)
		return
	}

	var req enqueueRequest
	body := http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if err := json.NewDecoder(body).Decode(&req); err != nil {
		msg := "Cuerpo JSON inválido"
		if errors.Is(err, io.EOF) {
			msg = "El cuerpo es obligatorio"
		}
		writeError(w, r, http.StatusBadRequest, CodeValidation, msg, nil)
		return
	}
	in, problems := req.validate()
	if len(problems) > 0 {
		writeError(w, r, http.StatusBadRequest, CodeValidation, "El pedido no es válido", problems)
		return
	}

	view, created, err := h.Svc.Enqueue(r.Context(), in)
	if err != nil {
		h.internal(w, r, err)
		return
	}
	status := http.StatusOK
	if created {
		status = http.StatusCreated
	}
	writeJSON(w, status, view)
}

type queueResponse struct {
	Items       []queue.View   `json:"items"`
	Capacity    queue.Capacity `json:"capacity"`
	GeneratedAt time.Time      `json:"generated_at"`
}

var listableStatuses = map[string]bool{
	queue.StatusPendiente: true, queue.StatusEnPreparacion: true, queue.StatusListo: true,
}

// Con solo kitchen:deliver se devuelven únicamente pedidos listo.
func (h *Handler) GetQueue(w http.ResponseWriter, r *http.Request) {
	p, ok := authorize(w, r, PermKitchenRead, PermKitchenDeliver)
	if !ok {
		return
	}

	statusFilter := r.URL.Query().Get("status")
	if statusFilter != "" && statusFilter != queue.StatusEntregado && !listableStatuses[statusFilter] {
		writeError(w, r, http.StatusBadRequest, CodeValidation, "status no válido",
			map[string]any{"status": "use pendiente, en_preparacion, listo o entregado"})
		return
	}

	views, capacity, err := h.Svc.Snapshot(r.Context())
	if err != nil {
		h.internal(w, r, err)
		return
	}

	onlyReady := !p.Has(PermKitchenUpdate)
	out := make([]queue.View, 0, len(views))
	for _, v := range views {
		if onlyReady && v.Status != queue.StatusListo {
			continue
		}
		// Los entregados ya no están en la cola activa.
		if statusFilter != "" && v.Status != statusFilter {
			continue
		}
		out = append(out, v)
	}
	writeJSON(w, http.StatusOK, queueResponse{Items: out, Capacity: capacity, GeneratedAt: time.Now().UTC()})
}

func (h *Handler) internal(w http.ResponseWriter, r *http.Request, err error) {
	h.Log.Error("error interno", "request_id", RequestID(r.Context()), "error", err.Error())
	writeError(w, r, http.StatusInternalServerError, CodeInternal, "Error interno del servidor", nil)
}

func orderIDFrom(r *http.Request) (string, bool) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		return "", false
	}
	return id.String(), true
}

func actorID(p Principal) *string {
	id, err := uuid.Parse(p.UserID)
	if err != nil {
		return nil
	}
	s := id.String()
	return &s
}

func (h *Handler) notFound(w http.ResponseWriter, r *http.Request) {
	writeError(w, r, http.StatusNotFound, CodeNotFound, "La comanda no existe", nil)
}

func (h *Handler) GetOrder(w http.ResponseWriter, r *http.Request) {
	p, ok := authorize(w, r, PermKitchenRead, PermKitchenDeliver)
	if !ok {
		return
	}
	id, ok := orderIDFrom(r)
	if !ok {
		h.notFound(w, r)
		return
	}
	view, found, err := h.Svc.Get(r.Context(), id)
	if err != nil {
		h.internal(w, r, err)
		return
	}
	if !found || (!p.Has(PermKitchenRead) && view.Status != queue.StatusListo) {
		h.notFound(w, r)
		return
	}
	writeJSON(w, http.StatusOK, view)
}

type statusRequest struct {
	Status string `json:"status"`
}

func (h *Handler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	p, ok := authorize(w, r, PermKitchenUpdate)
	if !ok {
		return
	}
	id, ok := orderIDFrom(r)
	if !ok {
		h.notFound(w, r)
		return
	}
	var req statusRequest
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBodyBytes)).Decode(&req); err != nil {
		writeError(w, r, http.StatusBadRequest, CodeValidation, "Cuerpo JSON inválido", nil)
		return
	}
	switch req.Status {
	case queue.StatusPendiente, queue.StatusEnPreparacion, queue.StatusListo, queue.StatusEntregado:
	default:
		writeError(w, r, http.StatusBadRequest, CodeValidation, "status no válido",
			map[string]any{"status": "use en_preparacion o listo"})
		return
	}

	view, err := h.Svc.ChangeStatus(r.Context(), id, req.Status, actorID(p))
	if err != nil {
		h.transitionError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, view)
}

func (h *Handler) DeliverOrder(w http.ResponseWriter, r *http.Request) {
	p, ok := authorize(w, r, PermKitchenDeliver)
	if !ok {
		return
	}
	id, ok := orderIDFrom(r)
	if !ok {
		h.notFound(w, r)
		return
	}
	view, err := h.Svc.Deliver(r.Context(), id, actorID(p))
	if err != nil {
		h.transitionError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, view)
}

func (h *Handler) transitionError(w http.ResponseWriter, r *http.Request, err error) {
	var invalid *queue.InvalidStateError
	var full *queue.CapacityError
	switch {
	case errors.Is(err, queue.ErrNotFound):
		h.notFound(w, r)
	case errors.As(err, &full):
		writeError(w, r, http.StatusConflict, CodeAtCapacity, "La cocina está al máximo de capacidad; el pedido sigue pendiente",
			map[string]any{"max": full.Max, "in_preparation": full.InPreparation})
	case errors.As(err, &invalid):
		writeError(w, r, http.StatusConflict, CodeInvalidState,
			"Transición inválida: "+invalid.Current+" -> "+invalid.Requested,
			map[string]any{"current_status": invalid.Current, "requested_status": invalid.Requested})
	default:
		h.internal(w, r, err)
	}
}
