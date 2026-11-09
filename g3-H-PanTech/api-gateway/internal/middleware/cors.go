package middleware

import (
	"net/http"
	"strings"

	httpx "api-gateway/internal/http"
)

// CORS permite solo los orígenes de CORS_ORIGINS. Las peticiones sin Origin (KDS de escritorio,
// Web SSR desde el servidor) pasan sin cambios.
func CORS(origins []string) func(http.Handler) http.Handler {
	allowed := make(map[string]bool, len(origins))
	for _, o := range origins {
		allowed[strings.TrimRight(o, "/")] = true
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin == "" {
				next.ServeHTTP(w, r)
				return
			}

			preflight := r.Method == http.MethodOptions && r.Header.Get("Access-Control-Request-Method") != ""
			w.Header().Add("Vary", "Origin")
			if !allowed[origin] {
				if preflight {
					httpx.WriteError(w, r, http.StatusForbidden, httpx.CodeForbidden, "Origen no permitido", nil)
					return
				}
				next.ServeHTTP(w, r)
				return
			}

			h := w.Header()
			h.Set("Access-Control-Allow-Origin", origin)
			h.Set("Access-Control-Allow-Credentials", "true")
			h.Set("Access-Control-Expose-Headers", "X-Request-Id, Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining")
			if preflight {
				h.Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
				h.Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept")
				h.Set("Access-Control-Max-Age", "600")
				w.WriteHeader(http.StatusNoContent)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
