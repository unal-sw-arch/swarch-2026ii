package proxy

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"time"

	httpx "api-gateway/internal/http"
)

var identityHeaders = []string{
	"X-User-Id", "X-User-Email", "X-Roles", "X-Permissions", "X-Level", "X-Service-Token", "X-Request-Id",
}

func StripIdentityHeaders(h http.Header) {
	for _, name := range identityHeaders {
		h.Del(name)
	}
}

// Authorization se conserva: Identity la usa.
func New(target *url.URL, log *slog.Logger) *httputil.ReverseProxy {
	return &httputil.ReverseProxy{
		Rewrite: func(pr *httputil.ProxyRequest) {
			pr.SetURL(target)
			pr.SetXForwarded()
			StripIdentityHeaders(pr.Out.Header)
			if id := httpx.RequestID(pr.In.Context()); id != "" {
				pr.Out.Header.Set("X-Request-Id", id)
			}
			for name, values := range IdentityFrom(pr.In.Context()) {
				pr.Out.Header[name] = values
			}
		},
		Transport: &http.Transport{
			DialContext:           (&net.Dialer{Timeout: 3 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
			MaxIdleConnsPerHost:   32,
			IdleConnTimeout:       60 * time.Second,
			ResponseHeaderTimeout: 30 * time.Second,
		},
		ErrorHandler: func(w http.ResponseWriter, r *http.Request, err error) {
			log.Warn("upstream", "request_id", httpx.RequestID(r.Context()), "target", target.Host, "error", err.Error())
			if errors.Is(err, context.Canceled) {
				return // el cliente cortó la conexión
			}
			var opErr *net.OpError
			if errors.As(err, &opErr) && opErr.Op == "dial" {
				httpx.WriteError(w, r, http.StatusServiceUnavailable, httpx.CodeServiceUnavailable,
					"El servicio no está disponible", nil)
				return
			}
			httpx.WriteError(w, r, http.StatusBadGateway, httpx.CodeBadGateway,
				"El servicio no respondió correctamente", nil)
		},
	}
}
