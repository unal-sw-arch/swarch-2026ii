package tests

import (
	"context"
	"errors"
	"fmt"
	"os/exec"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"

	"kitchen-service/internal/queue"
	"kitchen-service/internal/repository"
	"kitchen-service/migrations"
)

// startContainer levanta una imagen con Docker y devuelve host:puerto. Si Docker no está
// disponible (o corre con -short) el test se salta solo.
func startContainer(t *testing.T, image, internalPort string, env ...string) string {
	t.Helper()
	if testing.Short() {
		t.Skip("integración omitida con -short")
	}
	if err := exec.Command("docker", "info").Run(); err != nil {
		t.Skip("Docker no está disponible")
	}

	args := []string{"run", "-d", "--rm", "-p", "127.0.0.1::" + internalPort}
	for _, e := range env {
		args = append(args, "-e", e)
	}
	out, err := exec.Command("docker", append(args, image)...).CombinedOutput()
	if err != nil {
		t.Skipf("no se pudo iniciar %s: %s", image, strings.TrimSpace(string(out)))
	}
	id := strings.TrimSpace(string(out))
	t.Cleanup(func() { _ = exec.Command("docker", "rm", "-f", id).Run() })

	portOut, err := exec.Command("docker", "port", id, internalPort+"/tcp").Output()
	if err != nil {
		t.Fatalf("docker port: %v", err)
	}
	line := strings.Split(strings.TrimSpace(string(portOut)), "\n")[0]
	return "127.0.0.1:" + line[strings.LastIndex(line, ":")+1:]
}

func startRedis(t *testing.T) *redis.Client {
	t.Helper()
	addr := startContainer(t, "redis:7-alpine", "6379")
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	t.Cleanup(func() { rdb.Close() })
	waitFor(t, func() error { return rdb.Ping(t.Context()).Err() })
	return rdb
}

func startPostgres(t *testing.T) *repository.Postgres {
	t.Helper()
	addr := startContainer(t, "postgres:16-alpine", "5432", "POSTGRES_PASSWORD=test", "POSTGRES_USER=panaderia", "POSTGRES_DB=panaderia")
	url := fmt.Sprintf("postgres://panaderia:test@%s/panaderia?sslmode=disable", addr)
	var db *repository.Postgres
	waitFor(t, func() error {
		var err error
		db, err = repository.Connect(t.Context(), url, 1, 0)
		return err
	})
	t.Cleanup(db.Close)
	return db
}

func waitFor(t *testing.T, f func() error) {
	t.Helper()
	deadline := time.Now().Add(60 * time.Second)
	var err error
	for time.Now().Before(deadline) {
		if err = f(); err == nil {
			return
		}
		time.Sleep(500 * time.Millisecond)
	}
	t.Fatalf("el servicio no respondió a tiempo: %v", err)
}

func TestPostgresMigrationsAreIdempotent(t *testing.T) {
	db := startPostgres(t)
	ctx := t.Context()

	for i := 0; i < 2; i++ {
		if err := db.Migrate(ctx, migrations.FS); err != nil {
			t.Fatalf("migrar (pasada %d): %v", i+1, err)
		}
	}
	if err := db.SeedCapacity(ctx, 4); err != nil {
		t.Fatal(err)
	}
	if err := db.SeedCapacity(ctx, 9); err != nil {
		t.Fatal(err)
	}
	if max, err := db.Capacity(ctx); err != nil || max != 4 {
		t.Fatalf("la capacidad sembrada no se pisa: %d %v", max, err)
	}
}

func TestPostgresPrepTimesHistoryAndConcurrentMigrate(t *testing.T) {
	db := startPostgres(t)
	ctx := t.Context()

	var wg sync.WaitGroup
	errs := make(chan error, 4)
	for i := 0; i < 4; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			errs <- db.Migrate(ctx, migrations.FS)
		}()
	}
	wg.Wait()
	close(errs)
	for err := range errs {
		if err != nil {
			t.Fatalf("migraciones concurrentes: %v", err)
		}
	}

	if err := db.SetPrepTime(ctx, product, 7); err != nil {
		t.Fatal(err)
	}
	got, err := db.Lookup(ctx, []string{product, "66666666-6666-4666-8666-666666666666"})
	if err != nil || len(got) != 1 || got[product] != 7 {
		t.Fatalf("lookup = %v %v", got, err)
	}

	user := customer
	base := time.Now().UTC().Truncate(time.Millisecond)
	for i, st := range []string{"pendiente", "en_preparacion", "listo", "entregado"} {
		var by *string
		if i > 0 {
			by = &user
		}
		if err := db.Record(ctx, orderA, st, by, base.Add(time.Duration(i)*time.Second)); err != nil {
			t.Fatal(err)
		}
	}
	events, err := db.Events(ctx, orderA)
	if err != nil || len(events) != 4 {
		t.Fatalf("eventos = %v %v", events, err)
	}
	if events[0].Status != "pendiente" || events[0].ChangedBy != nil || events[3].Status != "entregado" || *events[3].ChangedBy != user {
		t.Fatalf("historial: %+v", events)
	}

	if err := db.Record(ctx, orderA, "inventado", nil, base); err == nil {
		t.Fatal("el CHECK de estados debe rechazar valores inválidos")
	}
}

func TestPrepTimesResolveThroughService(t *testing.T) {
	db := startPostgres(t)
	ctx := t.Context()
	if err := db.Migrate(ctx, migrations.FS); err != nil {
		t.Fatal(err)
	}
	if err := db.SetPrepTime(ctx, product, 9); err != nil {
		t.Fatal(err)
	}

	svc := &queue.Service{Prep: db, DefaultPrep: 5}
	items := []queue.OrderItem{
		{ProductID: product, Name: "Desayuno", Quantity: 1},
		{ProductID: "66666666-6666-4666-8666-666666666666", Name: "Sin tiempo", Quantity: 1},
		{ProductID: "77777777-7777-4777-8777-777777777777", Name: "Pan", Quantity: 1, PrepMinutes: 3},
	}
	resolved, prep, err := svc.ResolvePrepMinutes(ctx, items)
	if err != nil {
		t.Fatal(err)
	}
	if prep != 9 || resolved[0].PrepMinutes != 9 || resolved[1].PrepMinutes != 5 || resolved[2].PrepMinutes != 3 {
		t.Fatalf("prep = %d, ítems = %+v", prep, resolved)
	}
}

func TestRedisTransitionsAndCapacityRaceWithRealRedis(t *testing.T) {
	rdb := startRedis(t)
	store := queue.NewStore(rdb)
	svc := &queue.Service{Store: store, Aging: 0.5, Capacity: 3, DefaultPrep: 5}
	ctx := context.Background()

	const orders = 20
	ids := make([]string, orders)
	for i := range ids {
		ids[i] = fmt.Sprintf("00000000-0000-4000-8000-%012d", i+1)
		in := queue.EnqueueInput{OrderID: ids[i], CustomerID: customer, OrderType: "para_llevar", PaidAt: t0}
		if _, created, err := svc.Enqueue(ctx, in); err != nil || !created {
			t.Fatalf("encolar: %v %v", created, err)
		}
		if _, created, _ := svc.Enqueue(ctx, in); created {
			t.Fatal("el segundo encolado debe ser idempotente")
		}
	}

	var ok, full atomic.Int32
	var wg sync.WaitGroup
	for _, id := range ids {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, err := svc.ChangeStatus(ctx, id, "en_preparacion", nil)
			var capErr *queue.CapacityError
			switch {
			case err == nil:
				ok.Add(1)
			case errors.As(err, &capErr):
				full.Add(1)
			default:
				t.Errorf("error inesperado: %v", err)
			}
		}()
	}
	wg.Wait()
	if ok.Load() != 3 || full.Load() != orders-3 {
		t.Fatalf("iniciaron %d (máx 3), rechazados %d", ok.Load(), full.Load())
	}
	if n, _ := store.InPreparation(ctx); n != 3 {
		t.Fatalf("en preparación = %d", n)
	}

	var delivered string
	for _, id := range ids {
		if _, err := svc.ChangeStatus(ctx, id, "listo", nil); err == nil {
			if _, err := svc.Deliver(ctx, id, nil); err != nil {
				t.Fatalf("entregar: %v", err)
			}
			delivered = id
		}
	}
	if n, _ := store.InPreparation(ctx); n != 0 {
		t.Fatalf("tras entregar no debe quedar nada en preparación: %d", n)
	}
	if ttl := rdb.TTL(ctx, "kitchen:order:"+delivered).Val(); ttl <= 0 {
		t.Fatalf("el hash de lo entregado debe conservar un TTL, ttl=%v", ttl)
	}
}
