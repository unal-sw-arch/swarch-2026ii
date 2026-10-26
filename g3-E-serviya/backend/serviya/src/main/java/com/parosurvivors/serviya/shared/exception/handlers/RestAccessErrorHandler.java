package com.parosurvivors.serviya.shared.exception.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Emite un cuerpo {@link ErrorResponse} uniforme (mismo formato que {@link GlobalExceptionHandler}) para los
 * fallos de la cadena de filtros de Spring Security, que NO pasan por el {@code @ControllerAdvice}:
 * sin autenticacion valida -> 401 ({@link AuthenticationEntryPoint#commence}); autenticado pero sin el
 * rol requerido -> 403 ({@link AccessDeniedHandler#handle}). Usa el {@link ObjectMapper} gestionado por
 * Spring (con JavaTimeModule) para serializar el timestamp.
 */
@Component
@RequiredArgsConstructor
public class RestAccessErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Se requiere autenticacion");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "No tienes permiso para acceder a este recurso");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
