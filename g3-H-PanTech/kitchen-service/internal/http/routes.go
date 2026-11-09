package httpapi

import (
	"log/slog"
	"net/http"
)

func NewRouter(h *Handler, log *slog.Logger) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", h.Health)
	mux.HandleFunc("GET /ready", h.ReadyCheck)

	mux.HandleFunc("POST /queue/orders", h.EnqueueOrder)
	mux.HandleFunc("GET /queue", h.GetQueue)

	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		writeError(w, r, http.StatusNotFound, CodeNotFound, "Ruta no encontrada", nil)
	})

	return withRequestID(withRecover(log, withLogging(log, mux)))
}
