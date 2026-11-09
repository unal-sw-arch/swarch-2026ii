// Notifications Service: reads email events from the message broker (RabbitMQ)
// and sends the emails with the Resend API. The admin configures the Resend
// account through the HTTP API of this service.
package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
)

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func getSettingsHandler(w http.ResponseWriter, r *http.Request) {
	s, err := loadSettings()
	if err != nil {
		writeJSON(w, 500, map[string]string{"detail": "No fue posible leer la configuración"})
		return
	}
	writeJSON(w, 200, s.public())
}

func saveSettingsHandler(w http.ResponseWriter, r *http.Request) {
	var body Settings
	if json.NewDecoder(r.Body).Decode(&body) != nil {
		writeJSON(w, 400, map[string]string{"detail": "Datos no válidos"})
		return
	}
	s, err := saveSettings(body)
	if err != nil {
		writeJSON(w, 500, map[string]string{"detail": "No fue posible guardar la configuración"})
		return
	}
	writeJSON(w, 200, s.public())
}

func testEmailHandler(w http.ResponseWriter, r *http.Request) {
	var body struct {
		To string `json:"to"`
	}
	json.NewDecoder(r.Body).Decode(&body)
	if body.To == "" {
		writeJSON(w, 400, map[string]string{"detail": "Escribe el correo de destino"})
		return
	}
	result := process(Event{Type: "TEST", To: body.To, Name: "Administrador"})
	writeJSON(w, 200, result)
}

func listEmailsHandler(w http.ResponseWriter, r *http.Request) {
	emails, err := lastEmails(50)
	if err != nil {
		writeJSON(w, 500, map[string]string{"detail": "No fue posible leer el historial"})
		return
	}
	writeJSON(w, 200, emails)
}

func main() {
	connectDatabase(env("MONGO_URL", "mongodb://localhost:27017"), env("MONGO_DB", "notifications"))
	go consumeEvents(env("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"))

	http.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"status": "ok"})
	})
	http.HandleFunc("GET /api/notifications/settings", getSettingsHandler)
	http.HandleFunc("PUT /api/notifications/settings", saveSettingsHandler)
	http.HandleFunc("POST /api/notifications/test", testEmailHandler)
	http.HandleFunc("GET /api/notifications/emails", listEmailsHandler)

	port := env("PORT", "8005")
	log.Printf("Notifications service listening on port %s", port)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}
