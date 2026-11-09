package middleware

import (
	"context"
	"crypto/rsa"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"

	httpx "api-gateway/internal/http"
	"api-gateway/internal/jwks"
	"api-gateway/internal/proxy"
)

type KeyProvider interface {
	Key(ctx context.Context, kid string) (*rsa.PublicKey, error)
}

type Principal struct {
	UserID      string
	Email       string
	Roles       []string
	Permissions []string
	Level       int
}

func (p Principal) Has(permission string) bool {
	for _, have := range p.Permissions {
		if have == permission {
			return true
		}
	}
	return false
}

func (p Principal) Headers() http.Header {
	h := http.Header{}
	h.Set("X-User-Id", p.UserID)
	if p.Email != "" {
		h.Set("X-User-Email", p.Email)
	}
	h.Set("X-Roles", strings.Join(p.Roles, ","))
	h.Set("X-Permissions", strings.Join(p.Permissions, ","))
	h.Set("X-Level", strconv.Itoa(p.Level))
	return h
}

type principalKey struct{}

func PrincipalFrom(ctx context.Context) (Principal, bool) {
	p, ok := ctx.Value(principalKey{}).(Principal)
	return p, ok
}

// TODO verificar con Identity los claims: sub (UUID), roles, permissions y level.
type claims struct {
	jwt.RegisteredClaims
	Email       string   `json:"email"`
	Roles       []string `json:"roles"`
	Permissions []string `json:"permissions"`
	Level       int      `json:"level"`
}

type Validator struct {
	keys   KeyProvider
	parser *jwt.Parser
}

func NewValidator(keys KeyProvider, issuer, audience string) *Validator {
	return &Validator{
		keys: keys,
		parser: jwt.NewParser(
			jwt.WithValidMethods([]string{"RS256"}),
			jwt.WithIssuer(issuer),
			jwt.WithAudience(audience),
			jwt.WithExpirationRequired(),
			jwt.WithLeeway(5*time.Second),
		),
	}
}

func (v *Validator) Validate(ctx context.Context, raw string) (Principal, error) {
	var c claims
	_, err := v.parser.ParseWithClaims(raw, &c, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		return v.keys.Key(ctx, kid)
	})
	if err != nil {
		return Principal{}, err
	}
	if _, err := uuid.Parse(c.Subject); err != nil {
		return Principal{}, errors.New("sub no es un UUID")
	}
	if !headerSafe(c.Email) || !allHeaderSafe(c.Roles) || !allHeaderSafe(c.Permissions) {
		return Principal{}, errors.New("claims con caracteres no permitidos")
	}
	return Principal{UserID: c.Subject, Email: c.Email, Roles: c.Roles, Permissions: c.Permissions, Level: c.Level}, nil
}

func headerSafe(s string) bool {
	return !strings.ContainsFunc(s, func(r rune) bool { return unicode.IsControl(r) || r == ',' })
}

func allHeaderSafe(list []string) bool {
	for _, s := range list {
		if !headerSafe(s) {
			return false
		}
	}
	return true
}

// Reemplaza los encabezados de identidad del cliente con los del token.
func Authenticate(v *Validator) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			scheme, token, _ := strings.Cut(r.Header.Get("Authorization"), " ")
			token = strings.TrimSpace(token)
			if !strings.EqualFold(scheme, "Bearer") || token == "" {
				unauthenticated(w, r, "Falta el token de acceso")
				return
			}

			p, err := v.Validate(r.Context(), token)
			if err != nil {
				if errors.Is(err, jwks.ErrUnavailable) {
					httpx.WriteError(w, r, http.StatusServiceUnavailable, httpx.CodeServiceUnavailable,
						"No se pudo verificar el token en este momento", nil)
					return
				}
				unauthenticated(w, r, "Token inválido o vencido")
				return
			}

			ctx := context.WithValue(r.Context(), principalKey{}, p)
			ctx = proxy.WithIdentity(ctx, p.Headers())
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func unauthenticated(w http.ResponseWriter, r *http.Request, message string) {
	w.Header().Set("WWW-Authenticate", "Bearer")
	httpx.WriteError(w, r, http.StatusUnauthorized, httpx.CodeUnauthenticated, message, nil)
}
