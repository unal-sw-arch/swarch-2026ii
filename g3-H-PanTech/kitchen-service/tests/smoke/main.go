// Command smoke prueba de punta a punta el Gateway y Kitchen contra un stack levantado con
// Docker Compose: flujo de cocina, order.ready en RabbitMQ y pruebas de seguridad.
//
// Sirve un JWKS falso y firma los tokens de prueba, por eso el Gateway debe apuntar su
// JWKS_URL a este proceso (ver docker-compose.smoke.yml). Solo para pruebas locales: nunca
// forma parte de la imagen de producción.
package main

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"os"
	"time"

	"github.com/google/uuid"
)

type config struct {
	gateway      string
	kitchen      string
	rabbit       string
	rabbitUser   string
	rabbitPass   string
	serviceToken string
	jwksAddr     string
	pgContainer  string
	suite        string
	wait         time.Duration
	rateLimit    int
}

func main() {
	var cfg config
	flag.StringVar(&cfg.gateway, "gateway", "http://localhost:8080", "URL del API Gateway")
	flag.StringVar(&cfg.kitchen, "kitchen", "http://localhost:8001", "URL directa de Kitchen (solo pruebas)")
	flag.StringVar(&cfg.rabbit, "rabbit", "http://localhost:15672", "consola de RabbitMQ")
	flag.StringVar(&cfg.rabbitUser, "rabbit-user", "panaderia", "usuario de RabbitMQ")
	flag.StringVar(&cfg.rabbitPass, "rabbit-pass", "panaderia", "contraseña de RabbitMQ")
	flag.StringVar(&cfg.serviceToken, "service-token", os.Getenv("SERVICE_TOKEN"), "SERVICE_TOKEN del compose")
	flag.StringVar(&cfg.jwksAddr, "jwks-addr", ":9999", "dirección donde servir el JWKS falso")
	flag.StringVar(&cfg.pgContainer, "pg-container", "panaderia-p2-postgres", "contenedor de PostgreSQL (vacío = omitir)")
	flag.StringVar(&cfg.suite, "suite", "all", "smoke, security o all")
	flag.DurationVar(&cfg.wait, "wait", 90*time.Second, "espera máxima a que el stack esté listo")
	flag.IntVar(&cfg.rateLimit, "rate-limit", 100, "RATE_LIMIT_USER_PER_MINUTE configurado en el Gateway")
	flag.Parse()

	if cfg.serviceToken == "" {
		cfg.serviceToken = "cambia-este-secreto-en-tu-entorno-local"
	}

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		log.Fatal(err)
	}
	kid := "smoke-" + uuid.NewString()
	serveJWKS(cfg.jwksAddr, key, kid)

	s := &suite{cfg: cfg, key: key, kid: kid, client: &http.Client{Timeout: 15 * time.Second}}
	s.ids = newIdentities(s)

	ctx, cancel := context.WithTimeout(context.Background(), cfg.wait)
	defer cancel()
	if err := s.waitReady(ctx); err != nil {
		fmt.Println("FAIL el stack no está listo:", err)
		os.Exit(1)
	}

	if cfg.suite == "all" || cfg.suite == "smoke" {
		s.runSmoke()
	}
	if cfg.suite == "all" || cfg.suite == "security" {
		s.runSecurity()
	}

	fmt.Printf("\n%d verificaciones: %d correctas, %d fallidas, %d omitidas\n", s.passed+s.failed+s.skipped, s.passed, s.failed, s.skipped)
	if s.failed > 0 {
		os.Exit(1)
	}
}

func serveJWKS(addr string, key *rsa.PrivateKey, kid string) {
	pub := key.PublicKey
	doc := map[string]any{"keys": []map[string]string{{
		"kty": "RSA", "kid": kid, "use": "sig", "alg": "RS256",
		"n": base64.RawURLEncoding.EncodeToString(pub.N.Bytes()),
		"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(pub.E)).Bytes()),
	}}}
	mux := http.NewServeMux()
	mux.HandleFunc("/.well-known/jwks.json", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(doc)
	})
	go func() {
		if err := http.ListenAndServe(addr, mux); err != nil {
			log.Fatalf("JWKS falso: %v", err)
		}
	}()
}
