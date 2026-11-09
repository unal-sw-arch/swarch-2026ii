package middleware

import (
	"context"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"

	httpx "api-gateway/internal/http"
)

const (
	rateWindow  = time.Minute
	redisBudget = 300 * time.Millisecond
)

// Limiter cuenta peticiones por ventana fija de un minuto en Redis.
type Limiter struct {
	rdb *redis.Client
	log *slog.Logger
	now func() time.Time
}

// NewLimiter crea un limitador sobre Redis.
func NewLimiter(rdb *redis.Client, log *slog.Logger) *Limiter {
	return &Limiter{rdb: rdb, log: log, now: time.Now}
}

// RateLimitIP limita por IP del cliente. No confía en X-Forwarded-For.
func RateLimitIP(l *Limiter, limit int) func(http.Handler) http.Handler {
	return l.middleware("ip", limit, func(r *http.Request) string {
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			return r.RemoteAddr
		}
		return host
	})
}

// RateLimitUser limita por usuario; va después de Authenticate.
func RateLimitUser(l *Limiter, limit int) func(http.Handler) http.Handler {
	return l.middleware("user", limit, func(r *http.Request) string {
		p, _ := PrincipalFrom(r.Context())
		return p.UserID
	})
}

func (l *Limiter) middleware(scope string, limit int, id func(*http.Request) string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			who := id(r)
			if who == "" {
				next.ServeHTTP(w, r)
				return
			}

			now := l.now()
			window := now.Unix() / int64(rateWindow.Seconds())
			key := "ratelimit:" + scope + ":" + who + ":" + strconv.FormatInt(window, 10)

			ctx, cancel := context.WithTimeout(r.Context(), redisBudget)
			defer cancel()
			pipe := l.rdb.Pipeline()
			incr := pipe.Incr(ctx, key)
			pipe.Expire(ctx, key, 2*rateWindow)
			if _, err := pipe.Exec(ctx); err != nil {
				// Sin Redis no se bloquea el tráfico: el límite deja de aplicarse.
				l.log.Warn("rate limit sin redis", "request_id", httpx.RequestID(r.Context()), "error", err.Error())
				next.ServeHTTP(w, r)
				return
			}

			count := incr.Val()
			remaining := int64(limit) - count
			if remaining < 0 {
				remaining = 0
			}
			w.Header().Set("X-RateLimit-Limit", strconv.Itoa(limit))
			w.Header().Set("X-RateLimit-Remaining", strconv.FormatInt(remaining, 10))

			if count > int64(limit) {
				retry := int(rateWindow.Seconds()) - int(now.Unix()%int64(rateWindow.Seconds()))
				w.Header().Set("Retry-After", strconv.Itoa(retry))
				httpx.WriteError(w, r, http.StatusTooManyRequests, httpx.CodeRateLimited,
					"Demasiadas peticiones; intente de nuevo en unos segundos", map[string]any{"scope": scope})
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
