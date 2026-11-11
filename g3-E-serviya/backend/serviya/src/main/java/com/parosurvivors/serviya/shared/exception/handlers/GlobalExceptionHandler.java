package com.parosurvivors.serviya.shared.exception.handlers;

import com.parosurvivors.serviya.shared.dto.ErrorResponse;
import com.parosurvivors.serviya.shared.exceptions.BusinessRuleException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.RateLimitExceededException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones de aplicación a respuestas con el cuerpo 4.4 ({@code status, error, code, message,
 * correlationId}; se conservan {@code path}/{@code timestamp}). Clave (decisión #12 / M10): la
 * autenticación fallida es <b>401</b> ({@link UnauthorizedException}); la autorización de dominio
 * (propiedad/participación) es <b>403</b> ({@link ForbiddenException}). El cliente refresca ante 401, no 403.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(request, HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    /** Cuota agotada -> 429 + Retry-After, para que el cliente sepa cuánto esperar antes de reintentar. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body(request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException ex, HttpServletRequest request) {
        return build(request, HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(request, HttpStatus.BAD_REQUEST, "BUSINESS_RULE", ex.getMessage());
    }

    /** Autenticación fallida (login, baneo, token ausente/ inválido) -> 401. El cliente debe refrescar/reautenticar. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    /** Autenticado pero sin permiso (propiedad/participación) -> 403. El cliente NO debe refrescar (decisión #12). */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(request, HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        return build(request, HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        return build(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ha ocurrido un error inesperado");
    }

    private ResponseEntity<ErrorResponse> build(HttpServletRequest request, HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(body(request, status, code, message));
    }

    /** Cuerpo 4.4 con el correlationId de la petición (lo pone el gateway) o uno nuevo. */
    private ErrorResponse body(HttpServletRequest request, HttpStatus status, String code, String message) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .correlationId(correlationId(request))
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String correlationId(HttpServletRequest request) {
        String existing = request.getHeader("X-Request-Id");
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }
}
