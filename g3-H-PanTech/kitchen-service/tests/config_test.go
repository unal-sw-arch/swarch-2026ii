package tests

import (
	"strings"
	"testing"

	"kitchen-service/internal/config"
)

func getenvFrom(m map[string]string) func(string) string {
	return func(k string) string { return m[k] }
}

func TestConfigDefaultsAndDatabaseFallback(t *testing.T) {
	cfg, err := config.Load(getenvFrom(map[string]string{
		"REDIS_QUEUE_URL": "redis://r:6379/1", "SERVICE_TOKEN": "s", "RABBITMQ_URL": "amqp://x",
		"DATABASE_URL_GO": "postgres://go",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if cfg.DatabaseURL != "postgres://go" || cfg.Port != "8001" || cfg.AgingFactor != 0.5 ||
		cfg.KitchenCapacity != 4 || cfg.DefaultPrepMinutes != 5 || cfg.RabbitMQExchange != "panaderia.events" {
		t.Fatalf("cfg = %+v", cfg)
	}

	cfg, _ = config.Load(getenvFrom(map[string]string{
		"REDIS_QUEUE_URL": "r", "SERVICE_TOKEN": "s", "RABBITMQ_URL": "a",
		"DATABASE_URL": "postgres://canonica", "DATABASE_URL_GO": "postgres://go",
	}))
	if cfg.DatabaseURL != "postgres://canonica" {
		t.Fatalf("DATABASE_URL debe tener prioridad: %s", cfg.DatabaseURL)
	}
}

func TestConfigRejectsMissingAndInvalid(t *testing.T) {
	_, err := config.Load(getenvFrom(map[string]string{"AGING_FACTOR": "-1", "KITCHEN_CAPACITY": "0", "DEFAULT_PREP_MINUTES": "x"}))
	if err == nil {
		t.Fatal("debe rechazar la configuración")
	}
	for _, want := range []string{"REDIS_QUEUE_URL", "SERVICE_TOKEN", "DATABASE_URL", "RABBITMQ_URL", "AGING_FACTOR", "KITCHEN_CAPACITY", "DEFAULT_PREP_MINUTES"} {
		if !strings.Contains(err.Error(), want) {
			t.Fatalf("falta %s en: %v", want, err)
		}
	}
}
