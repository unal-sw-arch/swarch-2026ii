package router

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"

	"api-gateway/internal/config"
	httpx "api-gateway/internal/http"
	"api-gateway/internal/jwks"
	"api-gateway/internal/middleware"
	"api-gateway/internal/proxy"
)

type Gateway struct {
	cfg      config.Config
	rdb      *redis.Client
	log      *slog.Logger
	limiter  *middleware.Limiter
	auth     func(http.Handler) http.Handler
	handlers map[*Rule]http.Handler
}

type Option func(*Gateway)

func WithKeys(k middleware.KeyProvider) Option {
	return func(g *Gateway) {
		g.auth = middleware.Authenticate(middleware.NewValidator(k, g.cfg.JWTIssuer, g.cfg.JWTAudience))
	}
}

// Orden: request id, logging, CORS, rate limit IP, auth, rate limit usuario, permisos, proxy.
func New(cfg config.Config, rdb *redis.Client, log *slog.Logger, opts ...Option) http.Handler {
	g := &Gateway{cfg: cfg, rdb: rdb, log: log, limiter: middleware.NewLimiter(rdb, log), handlers: map[*Rule]http.Handler{}}
	WithKeys(jwks.New(cfg.JWKSURL))(g)
	for _, opt := range opts {
		opt(g)
	}

	proxies := map[Upstream]http.Handler{
		Identity:  proxy.New(cfg.IdentityURL, log),
		Order:     proxy.New(cfg.OrderURL, log),
		Kitchen:   proxy.New(cfg.KitchenURL, log),
		Reporting: proxy.New(cfg.ReportingURL, log),
	}
	for i := range Routes {
		for j := range Routes[i].Rules {
			rule := &Routes[i].Rules[j]
			g.handlers[rule] = g.chain(proxies[Routes[i].Upstream], rule)
		}
	}

	g.handlers[&denyAll] = g.chain(nil, &denyAll)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", g.health)
	mux.HandleFunc("GET /ready", g.ready)
	mux.Handle("/", middleware.RateLimitIP(g.limiter, cfg.RateLimitIPPerMinute)(http.HandlerFunc(g.route)))

	var h http.Handler = mux
	h = middleware.CORS(cfg.CORSOrigins)(h)
	h = middleware.Logging(log)(h)
	h = middleware.Recover(log)(h)
	h = middleware.RequestID(h)
	return h
}

func (g *Gateway) chain(final http.Handler, rule *Rule) http.Handler {
	switch rule.Access {
	case Public:
		return final
	case Authenticated:
		return g.auth(middleware.RateLimitUser(g.limiter, g.cfg.RateLimitUserPerMinute)(final))
	case AnyPermission:
		h := middleware.RequirePermissions(rule.AnyOf)(final)
		return g.auth(middleware.RateLimitUser(g.limiter, g.cfg.RateLimitUserPerMinute)(h))
	default:
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			httpx.WriteError(w, r, http.StatusForbidden, httpx.CodeForbidden, "Ruta no disponible desde el exterior", nil)
		})
	}
}

func (g *Gateway) health(w http.ResponseWriter, _ *http.Request) {
	httpx.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

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
	g.handlers[route.Decide(r.Method, r.URL.Path)].ServeHTTP(w, r)
}
