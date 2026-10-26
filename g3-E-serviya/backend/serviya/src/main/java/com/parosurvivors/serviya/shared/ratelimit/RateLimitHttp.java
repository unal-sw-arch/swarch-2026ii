package com.parosurvivors.serviya.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Escribe la respuesta {@code 429 Too Many Requests} de forma uniforme desde los filtros de rate limit.
 * Se escribe a mano (no via {@code @RestControllerAdvice}) porque estos filtros corren ANTES del
 * DispatcherServlet; el cuerpo usa el mismo {@link ErrorResponse} que el resto de la API.
 */
public final class RateLimitHttp {

    private RateLimitHttp() {
    }

    public static void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                            long retryAfterSeconds, ObjectMapper objectMapper) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Demasiadas peticiones. Intentalo de nuevo mas tarde.")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
