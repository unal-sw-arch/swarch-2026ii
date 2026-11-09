package queue

import (
	"context"
	"errors"
	"fmt"
)

// CapacityProvider entrega la capacidad máxima vigente (kitchen.kitchen_capacity).
type CapacityProvider interface {
	Capacity(ctx context.Context) (int, error)
}

var (
	// ErrNotFound indica que la comanda no existe (o ya expiró).
	ErrNotFound = errors.New("comanda no encontrada")
	// ErrAtCapacity indica que la cocina ya prepara el máximo de pedidos.
	ErrAtCapacity = errors.New("la cocina está al máximo de capacidad")
)

// InvalidStateError es una transición que el estado actual no permite.
type InvalidStateError struct {
	Current   string
	Requested string
}

func (e *InvalidStateError) Error() string {
	return fmt.Sprintf("transición inválida: %s -> %s", e.Current, e.Requested)
}

// CapacityError lleva la ocupación al rechazar un pedido por capacidad.
type CapacityError struct {
	Max           int
	InPreparation int
}

func (e *CapacityError) Error() string { return ErrAtCapacity.Error() }
func (e *CapacityError) Unwrap() error { return ErrAtCapacity }
