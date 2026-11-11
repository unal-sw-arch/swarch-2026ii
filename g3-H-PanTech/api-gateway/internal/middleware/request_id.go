// Package middleware: request id → logging → CORS → rate limit → auth → permisos → proxy.
package middleware

import (
	"net/http"

	"github.com/google/uuid"

	httpx "api-gateway/internal/http"
)

// RequestID genera un id nuevo por petición; nunca confía en el del cliente.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := uuid.NewString()
		w.Header().Set("X-Request-Id", id)
		next.ServeHTTP(w, r.WithContext(httpx.WithRequestID(r.Context(), id)))
	})
}
