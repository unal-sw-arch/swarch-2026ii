package middleware

import (
	"net/http"

	httpx "api-gateway/internal/http"
)

func RequirePermissions(anyOf []string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, ok := PrincipalFrom(r.Context())
			if !ok {
				unauthenticated(w, r, "Falta el token de acceso")
				return
			}
			for _, perm := range anyOf {
				if p.Has(perm) {
					next.ServeHTTP(w, r)
					return
				}
			}
			httpx.WriteError(w, r, http.StatusForbidden, httpx.CodeForbidden, "No tiene permiso para esta operación", nil)
		})
	}
}
