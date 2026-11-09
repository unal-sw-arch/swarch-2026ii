package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"

	"kitchen-service/internal/config"
	httpapi "kitchen-service/internal/http"
	"kitchen-service/internal/queue"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil)).With("service", "kitchen-service")

	cfg, err := config.Load(os.Getenv)
	if err != nil {
		log.Error("configuración", "error", err.Error())
		os.Exit(1)
	}

	redisOpts, err := redis.ParseURL(cfg.RedisQueueURL)
	if err != nil {
		log.Error("REDIS_QUEUE_URL inválida", "error", err.Error())
		os.Exit(1)
	}
	rdb := redis.NewClient(redisOpts)
	defer rdb.Close()

	store := queue.NewStore(rdb)
	svc := &queue.Service{
		Store:       store,
		Aging:       cfg.AgingFactor,
		Capacity:    cfg.KitchenCapacity,
		DefaultPrep: cfg.DefaultPrepMinutes,
	}

	handler := &httpapi.Handler{
		Svc:          svc,
		ServiceToken: cfg.ServiceToken,
		Log:          log,
		Ready: map[string]func(context.Context) error{
			"redis": store.Ping,
		},
	}

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           httpapi.NewRouter(handler, log),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Info("kitchen-service escuchando", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Error("servidor", "error", err.Error())
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Error("apagado", "error", err.Error())
	}
	log.Info("kitchen-service detenido")
}
