// API Gateway: the single entry point used by Kanasta Web and Kanasta Admin.
// It checks who is calling (asking the Accounts Service) and forwards each
// request to the service that owns that path.
package main

import (
	"encoding/json"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type route struct {
	prefix string
	target string
}

type user struct {
	ID    int    `json:"id"`
	Name  string `json:"name"`
	Email string `json:"email"`
	Role  string `json:"role"`
}

var (
	accountsURL = env("ACCOUNTS_URL", "http://localhost:8004")
	routes      = []route{
		{"/api/catalog/", env("CATALOG_URL", "http://localhost:8001")},
		{"/api/accounts/", accountsURL},
		{"/api/notifications/", env("NOTIFICATIONS_URL", "http://localhost:8005")},
		{"/graphql", env("ORDERS_URL", "http://localhost:8002")},
		{"/images", env("IMAGES_URL", "http://localhost:8003")},
	}
)

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func sendError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"detail": message})
}

// Asks the Accounts Service who owns the token. Returns nil if there is no valid session.
func currentUser(r *http.Request) *user {
	auth := r.Header.Get("Authorization")
	if !strings.HasPrefix(auth, "Bearer ") {
		return nil
	}
	request, _ := http.NewRequest("GET", accountsURL+"/api/accounts/session", nil)
	request.Header.Set("Authorization", auth)
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return nil
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil
	}
	var u user
	if json.NewDecoder(response.Body).Decode(&u) != nil {
		return nil
	}
	return &u
}

// Access rules. GraphQL operations are checked inside the Orders Service,
// because all of them use the same path. Stock changes are only made by the
// Orders Service inside the backend network, never from outside.
func access(r *http.Request) string {
	path, method := r.URL.Path, r.Method
	switch {
	case strings.Contains(path, "/reserve") || strings.Contains(path, "/release"):
		return "internal"
	case strings.HasSuffix(path, "/reviews") && method == "POST":
		return "user"
	case strings.HasPrefix(path, "/api/notifications/"),
		strings.HasPrefix(path, "/api/accounts/users"),
		strings.HasPrefix(path, "/api/catalog/") && method != "GET",
		strings.HasPrefix(path, "/images") && method != "GET":
		return "admin"
	}
	return "public"
}

func handler(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path == "/health" {
		w.Write([]byte(`{"status":"ok"}`))
		return
	}

	var target string
	for _, rt := range routes {
		if strings.HasPrefix(r.URL.Path, rt.prefix) {
			target = rt.target
			break
		}
	}
	if target == "" {
		sendError(w, http.StatusNotFound, "Ruta no encontrada")
		return
	}

	// Nobody outside can pretend to be someone else
	for _, header := range []string{"X-User-Id", "X-User-Name", "X-User-Email", "X-User-Role"} {
		r.Header.Del(header)
	}
	u := currentUser(r)
	if u != nil {
		r.Header.Set("X-User-Id", strconv.Itoa(u.ID))
		r.Header.Set("X-User-Name", url.QueryEscape(u.Name))
		r.Header.Set("X-User-Email", u.Email)
		r.Header.Set("X-User-Role", u.Role)
	}

	switch access(r) {
	case "internal":
		sendError(w, http.StatusForbidden, "Esta ruta solo la usan los servicios internos")
		return
	case "user":
		if u == nil {
			sendError(w, http.StatusUnauthorized, "Debes iniciar sesión")
			return
		}
	case "admin":
		if u == nil || u.Role != "ADMIN" {
			sendError(w, http.StatusForbidden, "Solo el administrador puede hacer esto")
			return
		}
	}

	targetURL, _ := url.Parse(target)
	log.Printf("%s %s -> %s", r.Method, r.URL.Path, target)
	httputil.NewSingleHostReverseProxy(targetURL).ServeHTTP(w, r)
}

func main() {
	port := env("PORT", "8000")
	log.Printf("API Gateway listening on port %s", port)
	log.Fatal(http.ListenAndServe(":"+port, http.HandlerFunc(handler)))
}
