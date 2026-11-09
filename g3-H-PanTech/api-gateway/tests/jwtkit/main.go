// jwtkit sirve un JWKS falso y firma JWT de prueba. Solo para pruebas locales.
package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"flag"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const kid = "kit-1"

func main() {
	if len(os.Args) < 2 {
		log.Fatal("uso: jwtkit serve|token [opciones]")
	}
	switch os.Args[1] {
	case "serve":
		serve(os.Args[2:])
	case "token":
		token(os.Args[2:])
	default:
		log.Fatalf("comando desconocido: %s", os.Args[1])
	}
}

func loadOrCreateKey(path string, create bool) *rsa.PrivateKey {
	if raw, err := os.ReadFile(path); err == nil {
		block, _ := pem.Decode(raw)
		key, err := x509.ParsePKCS1PrivateKey(block.Bytes)
		if err != nil {
			log.Fatal(err)
		}
		return key
	}
	if !create {
		log.Fatalf("no existe la clave %s; arranque primero `jwtkit serve`", path)
	}
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		log.Fatal(err)
	}
	block := &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)}
	if err := os.WriteFile(path, pem.EncodeToMemory(block), 0o600); err != nil {
		log.Fatal(err)
	}
	return key
}

func serve(args []string) {
	fs := flag.NewFlagSet("serve", flag.ExitOnError)
	addr := fs.String("addr", ":9999", "dirección de escucha")
	keyPath := fs.String("key", "key.pem", "ruta de la clave privada (se crea si no existe)")
	_ = fs.Parse(args)

	pub := loadOrCreateKey(*keyPath, true).PublicKey
	doc := map[string]any{"keys": []map[string]string{{
		"kty": "RSA", "kid": kid, "use": "sig", "alg": "RS256",
		"n": base64.RawURLEncoding.EncodeToString(pub.N.Bytes()),
		"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(pub.E)).Bytes()),
	}}}
	http.HandleFunc("/.well-known/jwks.json", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(doc)
	})
	log.Printf("JWKS falso en %s/.well-known/jwks.json", *addr)
	log.Fatal(http.ListenAndServe(*addr, nil))
}

func token(args []string) {
	fs := flag.NewFlagSet("token", flag.ExitOnError)
	keyPath := fs.String("key", "key.pem", "ruta de la clave privada")
	sub := fs.String("sub", "8f14e45f-ceea-467f-a0e6-1f3d3b6a1c11", "sub (UUID)")
	email := fs.String("email", "prueba@demo.com", "email")
	roles := fs.String("roles", "cocinero", "roles separados por coma")
	perms := fs.String("perms", "", "permisos separados por coma")
	level := fs.Int("level", 50, "nivel")
	issuer := fs.String("iss", "identity-service", "iss")
	audience := fs.String("aud", "panaderia-express", "aud")
	ttl := fs.Duration("ttl", 15*time.Minute, "vigencia (negativa = vencido)")
	_ = fs.Parse(args)

	now := time.Now()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"sub": *sub, "email": *email, "roles": split(*roles), "permissions": split(*perms), "level": *level,
		"iss": *issuer, "aud": *audience, "iat": now.Unix(), "exp": now.Add(*ttl).Unix(),
	})
	tok.Header["kid"] = kid
	signed, err := tok.SignedString(loadOrCreateKey(*keyPath, false))
	if err != nil {
		log.Fatal(err)
	}
	fmt.Println(signed)
}

func split(s string) []string {
	out := []string{}
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
