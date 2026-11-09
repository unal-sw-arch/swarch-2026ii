// Package events publica eventos de dominio en RabbitMQ (exchange panaderia.events).
package events

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

// Publisher publica un mensaje ya serializado.
type Publisher interface {
	Publish(ctx context.Context, routingKey, messageID string, body []byte) error
}

// Rabbit publica con confirmaciones del broker y reconecta solo cuando se cae la conexión.
type Rabbit struct {
	url      string
	exchange string

	mu   sync.Mutex
	conn *amqp.Connection
	ch   *amqp.Channel
}

// NewRabbit crea el publicador; no conecta hasta el primer uso.
func NewRabbit(url, exchange string) *Rabbit { return &Rabbit{url: url, exchange: exchange} }

func (r *Rabbit) ensure() (*amqp.Channel, error) {
	if r.conn != nil && !r.conn.IsClosed() && r.ch != nil && !r.ch.IsClosed() {
		return r.ch, nil
	}
	r.close()

	conn, err := amqp.DialConfig(r.url, amqp.Config{Dial: amqp.DefaultDial(3 * time.Second)})
	if err != nil {
		return nil, fmt.Errorf("conectar a rabbitmq: %w", err)
	}
	ch, err := conn.Channel()
	if err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("abrir canal: %w", err)
	}
	// Mismos argumentos que infrastructure/rabbitmq/definitions.json.
	if err := ch.ExchangeDeclare(r.exchange, "topic", true, false, false, false, nil); err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("declarar exchange: %w", err)
	}
	if err := ch.Confirm(false); err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("activar confirmaciones: %w", err)
	}
	r.conn, r.ch = conn, ch
	return ch, nil
}

func (r *Rabbit) close() {
	if r.conn != nil {
		_ = r.conn.Close()
	}
	r.conn, r.ch = nil, nil
}

// Publish envía el mensaje y espera la confirmación del broker.
func (r *Rabbit) Publish(ctx context.Context, routingKey, messageID string, body []byte) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	ch, err := r.ensure()
	if err != nil {
		return err
	}
	conf, err := ch.PublishWithDeferredConfirmWithContext(ctx, r.exchange, routingKey, false, false, amqp.Publishing{
		ContentType:  "application/json",
		DeliveryMode: amqp.Persistent,
		MessageId:    messageID,
		Type:         routingKey,
		Timestamp:    time.Now(),
		Body:         body,
	})
	if err != nil {
		r.close()
		return fmt.Errorf("publicar: %w", err)
	}
	ok, err := conf.WaitContext(ctx)
	if err != nil {
		r.close()
		return fmt.Errorf("esperar confirmación: %w", err)
	}
	if !ok {
		return errors.New("el broker rechazó el mensaje")
	}
	return nil
}

// Ping verifica que se pueda conectar al broker.
func (r *Rabbit) Ping(_ context.Context) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	_, err := r.ensure()
	return err
}

// Close cierra la conexión.
func (r *Rabbit) Close() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.close()
}
