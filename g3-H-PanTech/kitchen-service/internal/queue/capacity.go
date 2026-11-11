package queue

import (
	"context"
	"errors"
	"fmt"
)

type CapacityProvider interface {
	Capacity(ctx context.Context) (int, error)
}

var (
	ErrNotFound   = errors.New("comanda no encontrada")
	ErrAtCapacity = errors.New("la cocina está al máximo de capacidad")
)

type InvalidStateError struct {
	Current   string
	Requested string
}

func (e *InvalidStateError) Error() string {
	return fmt.Sprintf("transición inválida: %s -> %s", e.Current, e.Requested)
}

type CapacityError struct {
	Max           int
	InPreparation int
}

func (e *CapacityError) Error() string { return ErrAtCapacity.Error() }
func (e *CapacityError) Unwrap() error { return ErrAtCapacity }
