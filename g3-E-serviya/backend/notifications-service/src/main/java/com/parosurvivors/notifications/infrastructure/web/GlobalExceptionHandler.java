package com.parosurvivors.notifications.infrastructure.web;

import com.parosurvivors.notifications.application.exceptions.ResourceNotFoundException;
import com.parosurvivors.notifications.application.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    /** Filtro inválido (p. ej. {@code status=FOO}): es un error del cliente, no un 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Parametro invalido", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(),
                code, message, correlationId(request), request.getRequestURI(), LocalDateTime.now()));
    }

    static String correlationId(HttpServletRequest request) {
        String existing = request.getHeader("X-Request-Id");
        return (existing != null && !existing.isBlank()) ? existing : java.util.UUID.randomUUID().toString();
    }
}
