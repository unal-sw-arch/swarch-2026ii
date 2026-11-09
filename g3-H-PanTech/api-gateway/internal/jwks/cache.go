// Package jwks obtiene y cachea las claves públicas RSA de Identity.
package jwks

import (
	"context"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"sync"
	"time"
)

var (
	// ErrKeyNotFound indica que el kid no está en el JWKS vigente.
	ErrKeyNotFound = errors.New("jwks: clave no encontrada")
	// ErrUnavailable indica que no se pudo obtener el JWKS y no hay clave en caché.
	ErrUnavailable = errors.New("jwks: no disponible")
)

// Cache guarda las claves y las renueva al vencer el TTL o ante un kid desconocido.
type Cache struct {
	url        string
	client     *http.Client
	ttl        time.Duration
	minRefresh time.Duration
	now        func() time.Time

	mu          sync.RWMutex
	keys        map[string]*rsa.PublicKey
	fetchedAt   time.Time
	lastAttempt time.Time
}

// Option ajusta el caché.
type Option func(*Cache)

// WithTTL fija cuánto vive una clave antes de renovar el JWKS (por defecto 10 min).
func WithTTL(d time.Duration) Option { return func(c *Cache) { c.ttl = d } }

// WithMinRefresh fija el intervalo mínimo entre consultas al JWKS (por defecto 10 s).
func WithMinRefresh(d time.Duration) Option { return func(c *Cache) { c.minRefresh = d } }

// WithClock reemplaza el reloj (pruebas).
func WithClock(now func() time.Time) Option { return func(c *Cache) { c.now = now } }

// New crea un caché para la URL del JWKS.
func New(url string, opts ...Option) *Cache {
	c := &Cache{
		url:        url,
		client:     &http.Client{Timeout: 3 * time.Second},
		ttl:        10 * time.Minute,
		minRefresh: 10 * time.Second,
		now:        time.Now,
		keys:       map[string]*rsa.PublicKey{},
	}
	for _, opt := range opts {
		opt(c)
	}
	return c
}

// Key devuelve la clave del kid. Sin kid solo acepta un JWKS de una única clave.
func (c *Cache) Key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	c.mu.RLock()
	key, fresh := c.lookup(kid)
	c.mu.RUnlock()
	if key != nil && fresh {
		return key, nil
	}

	c.mu.Lock()
	defer c.mu.Unlock()
	key, fresh = c.lookup(kid)
	if key != nil && fresh {
		return key, nil
	}

	now := c.now()
	if key == nil && !c.lastAttempt.IsZero() && now.Sub(c.lastAttempt) < c.minRefresh {
		if len(c.keys) == 0 {
			return nil, ErrUnavailable
		}
		return nil, ErrKeyNotFound
	}

	c.lastAttempt = now
	keys, err := c.fetch(ctx)
	if err != nil {
		if key != nil {
			return key, nil // se sigue usando la clave vencida mientras Identity no responde
		}
		return nil, fmt.Errorf("%w: %v", ErrUnavailable, err)
	}
	c.keys = keys
	c.fetchedAt = now

	if key, _ = c.lookup(kid); key == nil {
		return nil, ErrKeyNotFound
	}
	return key, nil
}

func (c *Cache) lookup(kid string) (*rsa.PublicKey, bool) {
	fresh := !c.fetchedAt.IsZero() && c.now().Sub(c.fetchedAt) < c.ttl
	if kid == "" {
		if len(c.keys) == 1 {
			for _, k := range c.keys {
				return k, fresh
			}
		}
		return nil, false
	}
	return c.keys[kid], fresh
}

type jwk struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Use string `json:"use"`
	N   string `json:"n"`
	E   string `json:"e"`
}

func (c *Cache) fetch(ctx context.Context) (map[string]*rsa.PublicKey, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.url, nil)
	if err != nil {
		return nil, err
	}
	res, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("el JWKS respondió %d", res.StatusCode)
	}

	var doc struct {
		Keys []jwk `json:"keys"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, 1<<20)).Decode(&doc); err != nil {
		return nil, fmt.Errorf("JWKS inválido: %w", err)
	}

	keys := map[string]*rsa.PublicKey{}
	for _, k := range doc.Keys {
		if k.Kty != "RSA" || k.Use == "enc" {
			continue
		}
		pub, err := k.publicKey()
		if err != nil {
			continue
		}
		keys[k.Kid] = pub
	}
	if len(keys) == 0 {
		return nil, errors.New("el JWKS no trae claves RSA válidas")
	}
	return keys, nil
}

func (k jwk) publicKey() (*rsa.PublicKey, error) {
	n, err := base64.RawURLEncoding.DecodeString(k.N)
	if err != nil {
		return nil, err
	}
	e, err := base64.RawURLEncoding.DecodeString(k.E)
	if err != nil {
		return nil, err
	}
	exp := new(big.Int).SetBytes(e)
	if !exp.IsInt64() || exp.Int64() < 3 {
		return nil, errors.New("exponente inválido")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(exp.Int64())}, nil
}
