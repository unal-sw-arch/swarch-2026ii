package com.parosurvivors.chat.shared.infrastructure.web;

import com.parosurvivors.chat.shared.domain.DomainException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> domain(DomainException ex) {
        HttpStatus status = ProblemResponses.statusFor(ex.code());
        ResponseEntity<Map<String, Object>> response = ProblemResponses.problem(status, ex.code(), ex.getMessage());
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return ResponseEntity.status(status).header("Retry-After", "10")
                    .contentType(response.getHeaders().getContentType()).body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception ex) {
        return ProblemResponses.problem(HttpStatus.BAD_REQUEST, "CHAT_BAD_REQUEST", "Solicitud inválida");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        // Nunca se devuelve ni se registra el contenido de la petición (RNF-CHAT-13): solo el tipo y el mensaje.
        log.error("Error no controlado", ex);
        return ProblemResponses.problem(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_INTERNAL_ERROR", "Error interno");
    }
}
