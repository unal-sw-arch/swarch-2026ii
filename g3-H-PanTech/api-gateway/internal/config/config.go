// Package config lee las variables de entorno.
package config

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

type Config struct {
	Port         string
	IdentityURL  *url.URL
	OrderURL     *url.URL
	KitchenURL   *url.URL
	ReportingURL *url.URL
	RedisURL     string
	JWKSURL      string
	JWTIssuer    string
	JWTAudience  string
	ServiceToken string
	CORSOrigins  []string

	// Variables propias, ver axel-notas-integracion.md.
	RateLimitUserPerMinute int
	RateLimitIPPerMinute   int
}

func Load(getenv func(string) string) (Config, error) {
	cfg := Config{
		Port:                   valueOr(getenv("PORT"), "8080"),
		RedisURL:               getenv("REDIS_URL"),
		JWKSURL:                getenv("JWKS_URL"),
		JWTIssuer:              getenv("JWT_ISSUER"),
		JWTAudience:            getenv("JWT_AUDIENCE"),
		ServiceToken:           getenv("SERVICE_TOKEN"),
		CORSOrigins:            splitCSV(getenv("CORS_ORIGINS")),
		RateLimitUserPerMinute: 120,
		RateLimitIPPerMinute:   300,
	}

	var problems []string
	for name, dst := range map[string]**url.URL{
		"IDENTITY_URL":  &cfg.IdentityURL,
		"ORDER_URL":     &cfg.OrderURL,
		"KITCHEN_URL":   &cfg.KitchenURL,
		"REPORTING_URL": &cfg.ReportingURL,
	} {
		u, err := parseBaseURL(getenv(name))
		if err != nil {
			problems = append(problems, fmt.Sprintf("%s: %v", name, err))
			continue
		}
		*dst = u
	}
	for name, v := range map[string]string{
		"REDIS_URL":    cfg.RedisURL,
		"JWKS_URL":     cfg.JWKSURL,
		"JWT_ISSUER":   cfg.JWTIssuer,
		"JWT_AUDIENCE": cfg.JWTAudience,
	} {
		if v == "" {
			problems = append(problems, name+" es obligatoria")
		}
	}

	for name, dst := range map[string]*int{
		"RATE_LIMIT_USER_PER_MINUTE": &cfg.RateLimitUserPerMinute,
		"RATE_LIMIT_IP_PER_MINUTE":   &cfg.RateLimitIPPerMinute,
	} {
		if raw := getenv(name); raw != "" {
			v, err := strconv.Atoi(raw)
			if err != nil || v < 1 {
				problems = append(problems, name+" debe ser un entero >= 1")
				continue
			}
			*dst = v
		}
	}

	if len(problems) > 0 {
		return Config{}, fmt.Errorf("configuración inválida: %s", strings.Join(problems, "; "))
	}
	return cfg, nil
}

func parseBaseURL(raw string) (*url.URL, error) {
	if raw == "" {
		return nil, fmt.Errorf("es obligatoria")
	}
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return nil, fmt.Errorf("debe ser una URL http(s) con host")
	}
	return u, nil
}

func splitCSV(raw string) []string {
	var out []string
	for _, part := range strings.Split(raw, ",") {
		if part = strings.TrimSpace(part); part != "" {
			out = append(out, part)
		}
	}
	return out
}

func valueOr(value, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}
