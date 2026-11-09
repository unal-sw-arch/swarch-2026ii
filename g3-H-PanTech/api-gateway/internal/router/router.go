// Package router enruta por prefijo hacia los servicios.
package router

import (
	"context"
	"log/slog"
	"net/http"
	"net/http/httputil"
	"time"

	"github.com/redis/go-redis/v9"

	"api-gateway/internal/config"
	httpx "api-gateway/internal/http"
	"api-gateway/internal/middleware"
	"api-gateway/internal/proxy"
)

type Gateway struct {
	cfg     config.Config
	rdb     *redis.Client
	log     *slog.Logger
	proxies map[Upstream]*httputil.ReverseProxy
}

func New(cfg config.Config, rdb *redis.Client, log *slog.Logger) http.Handler {
	g := &Gateway{cfg: cfg, rdb: rdb, log: log, proxies: map[Upstream]*httputil.ReverseProxy{
		Identity:  proxy.New(cfg.IdentityURL, log),
		Order:     proxy.New(cfg.OrderURL, log),
		Kitchen:   proxy.New(cfg.KitchenURL, log),
		Reporting: proxy.New(cfg.ReportingURL, log),
	}}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", g.health)
	mux.HandleFunc("GET /ready", g.ready)
	mux.HandleFunc("/", g.route)

	var h http.Handler = mux
	h = middleware.Logging(log)(h)
	h = middleware.Recover(log)(h)
	h = middleware.RequestID(h)
	return h
}

func (g *Gateway) health(w http.ResponseWriter, _ *http.Request) {
	httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// ready verifica solo Redis.
func (g *Gateway) ready(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := g.rdb.Ping(ctx).Err(); err != nil {
		g.log.Warn("redis no disponible", "request_id", httpx.RequestID(r.Context()), "error", err.Error())
		httpx.WriteJSON(w, http.StatusServiceUnavailable, map[string]any{
			"status":       "not_ready",
			"dependencies": map[string]string{"redis": "down"},
		})
		return
	}
	httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ready"})
}

func (g *Gateway) route(w http.ResponseWriter, r *http.Request) {
	route, ok := Match(r.URL.Path)
	if !ok {
		httpx.WriteError(w, r, http.StatusNotFound, httpx.CodeNotFound, "Ruta no encontrada", nil)
		return
	}

	rule := route.Decide(r.Method, r.URL.Path)
	if rule.Access == Blocked {
		httpx.WriteError(w, r, http.StatusForbidden, httpx.CodeForbidden, "Ruta no disponible desde el exterior", nil)
		return
	}
	// TODO etapa 2: validar JWT y permisos.

	g.proxies[route.Upstream].ServeHTTP(w, r)
}
