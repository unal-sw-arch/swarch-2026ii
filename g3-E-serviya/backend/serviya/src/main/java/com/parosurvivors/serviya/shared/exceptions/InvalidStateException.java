package com.parosurvivors.serviya.shared.exceptions;

/**
 * Lanzada cuando se intenta una transición de estado inválida o se viola una
 * restricción de unicidad. El {@code GlobalExceptionHandler} la mapea a HTTP 409.
 * Ver CLAUDE.md ("Status codes").
 */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }

    public InvalidStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
