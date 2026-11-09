package httpapi

import (
	"crypto/subtle"
	"net/http"
	"strings"
)

const (
	PermKitchenRead    = "kitchen:read"
	PermKitchenUpdate  = "kitchen:update"
	PermKitchenDeliver = "kitchen:deliver"
)

type Principal struct {
	UserID      string
	Email       string
	Permissions map[string]bool
}

func (p Principal) Has(permission string) bool { return p.Permissions[permission] }

func principalFrom(r *http.Request) (Principal, bool) {
	userID := strings.TrimSpace(r.Header.Get("X-User-Id"))
	if userID == "" {
		return Principal{}, false
	}
	perms := map[string]bool{}
	for _, p := range strings.Split(r.Header.Get("X-Permissions"), ",") {
		if p = strings.TrimSpace(p); p != "" {
			perms[p] = true
		}
	}
	return Principal{UserID: userID, Email: r.Header.Get("X-User-Email"), Permissions: perms}, true
}

// authorize responde 401 sin X-User-Id y 403 sin ninguno de los permisos.
func authorize(w http.ResponseWriter, r *http.Request, anyOf ...string) (Principal, bool) {
	p, ok := principalFrom(r)
	if !ok {
		writeError(w, r, http.StatusUnauthorized, CodeUnauthenticated, "Falta la identidad del usuario", nil)
		return Principal{}, false
	}
	for _, perm := range anyOf {
		if p.Has(perm) {
			return p, true
		}
	}
	writeError(w, r, http.StatusForbidden, CodeForbidden, "No tiene permiso para esta operación", nil)
	return Principal{}, false
}

func validServiceToken(r *http.Request, expected string) bool {
	got := r.Header.Get("X-Service-Token")
	if got == "" || expected == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(expected)) == 1
}
