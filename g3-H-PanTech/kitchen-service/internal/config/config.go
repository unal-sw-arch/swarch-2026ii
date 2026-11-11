// Package config lee las variables de entorno.
package config

import (
	"fmt"
	"strconv"
	"strings"
)

type Config struct {
	Port               string
	DatabaseURL        string
	RedisQueueURL      string
	RabbitMQURL        string
	RabbitMQExchange   string
	ServiceToken       string
	AgingFactor        float64
	KitchenCapacity    int
	DefaultPrepMinutes int
}

// DATABASE_URL tiene prioridad; si está vacía se usa DATABASE_URL_GO.
func Load(getenv func(string) string) (Config, error) {
	cfg := Config{
		Port:               valueOr(getenv("PORT"), "8001"),
		DatabaseURL:        valueOr(getenv("DATABASE_URL"), getenv("DATABASE_URL_GO")),
		RedisQueueURL:      getenv("REDIS_QUEUE_URL"),
		RabbitMQURL:        getenv("RABBITMQ_URL"),
		RabbitMQExchange:   valueOr(getenv("RABBITMQ_EXCHANGE"), "panaderia.events"),
		ServiceToken:       getenv("SERVICE_TOKEN"),
		AgingFactor:        0.5,
		KitchenCapacity:    4,
		DefaultPrepMinutes: 5,
	}

	var problems []string
	if cfg.RedisQueueURL == "" {
		problems = append(problems, "REDIS_QUEUE_URL es obligatoria")
	}
	if cfg.ServiceToken == "" {
		problems = append(problems, "SERVICE_TOKEN es obligatoria")
	}
	if cfg.DatabaseURL == "" {
		problems = append(problems, "DATABASE_URL (o DATABASE_URL_GO) es obligatoria")
	}
	if cfg.RabbitMQURL == "" {
		problems = append(problems, "RABBITMQ_URL es obligatoria")
	}

	if raw := getenv("AGING_FACTOR"); raw != "" {
		v, err := strconv.ParseFloat(raw, 64)
		if err != nil || v < 0 {
			problems = append(problems, "AGING_FACTOR debe ser un número >= 0")
		} else {
			cfg.AgingFactor = v
		}
	}
	if raw := getenv("KITCHEN_CAPACITY"); raw != "" {
		v, err := strconv.Atoi(raw)
		if err != nil || v < 1 {
			problems = append(problems, "KITCHEN_CAPACITY debe ser un entero >= 1")
		} else {
			cfg.KitchenCapacity = v
		}
	}
	if raw := getenv("DEFAULT_PREP_MINUTES"); raw != "" {
		v, err := strconv.Atoi(raw)
		if err != nil || v < 1 {
			problems = append(problems, "DEFAULT_PREP_MINUTES debe ser un entero >= 1")
		} else {
			cfg.DefaultPrepMinutes = v
		}
	}

	if len(problems) > 0 {
		return Config{}, fmt.Errorf("configuración inválida: %s", strings.Join(problems, "; "))
	}
	return cfg, nil
}

func valueOr(value, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}
