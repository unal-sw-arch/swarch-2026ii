package main

import (
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

func main() {
	apiTarget := mustURL("API_TARGET", "http://courses-service:8000")
	graphqlTarget := mustURL("GRAPHQL_TARGET", "http://grades-service:4000")
	notificationsTarget := mustURL("NOTIFICATIONS_TARGET", "http://notifications-service:8081")
	statsTarget := mustURL("STATS_TARGET", "http://stats-service:8082")

	mux := http.NewServeMux()
	mux.HandleFunc("/health", health)
	mux.Handle("/api", proxy(apiTarget))
	mux.Handle("/api/", proxy(apiTarget))
	mux.Handle("/graphql", proxy(graphqlTarget))
	mux.Handle("/notifications", proxy(notificationsTarget))
	mux.Handle("/stats", proxy(statsTarget))
	mux.Handle("/stats/", proxy(statsTarget))

	server := &http.Server{
		Addr:              ":" + env("PORT", "8080"),
		Handler:           logging(mux),
		ReadHeaderTimeout: 10 * time.Second,
	}
	log.Printf("api gateway listening on %s", server.Addr)
	log.Fatal(server.ListenAndServe())
}

func proxy(target *url.URL) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		proxyURL := *target
		proxyURL.Path = strings.TrimRight(target.Path, "/") + r.URL.Path
		proxyURL.RawQuery = r.URL.RawQuery

		outbound, err := http.NewRequestWithContext(r.Context(), r.Method, proxyURL.String(), r.Body)
		if err != nil {
			http.Error(w, "bad upstream request", http.StatusBadGateway)
			return
		}
		outbound.Header = r.Header.Clone()
		outbound.Host = target.Host

		response, err := http.DefaultClient.Do(outbound)
		if err != nil {
			http.Error(w, "upstream unavailable", http.StatusBadGateway)
			return
		}
		defer response.Body.Close()
		for key, values := range response.Header {
			for _, value := range values {
				w.Header().Add(key, value)
			}
		}
		w.WriteHeader(response.StatusCode)
		_, _ = io.Copy(w, response.Body)
	})
}

func health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}

func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		log.Printf("%s %s", r.Method, r.URL.Path)
		next.ServeHTTP(w, r)
	})
}

func mustURL(name, fallback string) *url.URL {
	value := env(name, fallback)
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		log.Fatalf("%s must be an absolute URL", name)
	}
	return parsed
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}