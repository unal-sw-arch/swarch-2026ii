package repository

import (
	"context"
	"fmt"
	"io/fs"
	"sort"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

type Postgres struct {
	pool *pgxpool.Pool
}

func Connect(ctx context.Context, url string, attempts int, delay time.Duration) (*Postgres, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, fmt.Errorf("DATABASE_URL inválida: %w", err)
	}
	var last error
	for i := 0; i < attempts; i++ {
		if last = pool.Ping(ctx); last == nil {
			return &Postgres{pool: pool}, nil
		}
		select {
		case <-ctx.Done():
			pool.Close()
			return nil, ctx.Err()
		case <-time.After(delay):
		}
	}
	pool.Close()
	return nil, fmt.Errorf("postgres no responde: %w", last)
}

func New(pool *pgxpool.Pool) *Postgres { return &Postgres{pool: pool} }

func (p *Postgres) Close() { p.pool.Close() }

func (p *Postgres) Ping(ctx context.Context) error { return p.pool.Ping(ctx) }

// Idempotente; el advisory lock evita choques entre réplicas.
func (p *Postgres) Migrate(ctx context.Context, files fs.FS) error {
	conn, err := p.pool.Acquire(ctx)
	if err != nil {
		return err
	}
	defer conn.Release()

	if _, err := conn.Exec(ctx, `SELECT pg_advisory_lock(hashtext('kitchen-migrations'))`); err != nil {
		return err
	}
	defer conn.Exec(context.Background(), `SELECT pg_advisory_unlock(hashtext('kitchen-migrations'))`) //nolint:errcheck

	if _, err := conn.Exec(ctx, `CREATE SCHEMA IF NOT EXISTS kitchen;
		CREATE TABLE IF NOT EXISTS kitchen.schema_migrations (
			version text PRIMARY KEY,
			applied_at timestamptz NOT NULL DEFAULT now())`); err != nil {
		return err
	}

	names, err := fs.Glob(files, "*.sql")
	if err != nil {
		return err
	}
	sort.Strings(names)

	for _, name := range names {
		var applied bool
		if err := conn.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM kitchen.schema_migrations WHERE version = $1)`, name).Scan(&applied); err != nil {
			return err
		}
		if applied {
			continue
		}
		script, err := fs.ReadFile(files, name)
		if err != nil {
			return err
		}
		tx, err := conn.Begin(ctx)
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, string(script)); err != nil {
			_ = tx.Rollback(ctx)
			return fmt.Errorf("migración %s: %w", name, err)
		}
		if _, err := tx.Exec(ctx, `INSERT INTO kitchen.schema_migrations (version) VALUES ($1)`, name); err != nil {
			_ = tx.Rollback(ctx)
			return err
		}
		if err := tx.Commit(ctx); err != nil {
			return err
		}
	}
	return nil
}

// Solo siembra si la fila no existe: después manda la tabla.
func (p *Postgres) SeedCapacity(ctx context.Context, max int) error {
	_, err := p.pool.Exec(ctx,
		`INSERT INTO kitchen.kitchen_capacity (id, max_orders) VALUES (1, $1) ON CONFLICT (id) DO NOTHING`, max)
	return err
}

func (p *Postgres) Capacity(ctx context.Context) (int, error) {
	var max int
	err := p.pool.QueryRow(ctx, `SELECT max_orders FROM kitchen.kitchen_capacity WHERE id = 1`).Scan(&max)
	return max, err
}

func (p *Postgres) Lookup(ctx context.Context, productIDs []string) (map[string]int, error) {
	out := map[string]int{}
	if len(productIDs) == 0 {
		return out, nil
	}
	rows, err := p.pool.Query(ctx,
		`SELECT product_id, prep_minutes FROM kitchen.prep_times WHERE product_id = ANY($1::text[])`, productIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id string
		var minutes int
		if err := rows.Scan(&id, &minutes); err != nil {
			return nil, err
		}
		out[id] = minutes
	}
	return out, rows.Err()
}

func (p *Postgres) SetPrepTime(ctx context.Context, productID string, minutes int) error {
	_, err := p.pool.Exec(ctx, `INSERT INTO kitchen.prep_times (product_id, prep_minutes) VALUES ($1, $2)
		ON CONFLICT (product_id) DO UPDATE SET prep_minutes = EXCLUDED.prep_minutes, updated_at = now()`, productID, minutes)
	return err
}

func (p *Postgres) Record(ctx context.Context, orderID, status string, changedBy *string, at time.Time) error {
	_, err := p.pool.Exec(ctx,
		`INSERT INTO kitchen.order_status_events (order_id, status, changed_at, changed_by) VALUES ($1, $2, $3, $4)`,
		orderID, status, at, changedBy)
	return err
}

func (p *Postgres) Events(ctx context.Context, orderID string) ([]StatusEvent, error) {
	rows, err := p.pool.Query(ctx, `SELECT order_id::text, status, changed_at, changed_by::text
		FROM kitchen.order_status_events WHERE order_id = $1 ORDER BY changed_at, id`, orderID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []StatusEvent
	for rows.Next() {
		var e StatusEvent
		if err := rows.Scan(&e.OrderID, &e.Status, &e.ChangedAt, &e.ChangedBy); err != nil {
			return nil, err
		}
		out = append(out, e)
	}
	return out, rows.Err()
}
