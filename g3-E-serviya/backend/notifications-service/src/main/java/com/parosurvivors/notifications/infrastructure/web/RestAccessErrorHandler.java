package com.parosurvivors.notifications.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Cuerpo {@link ErrorResponse} para los fallos de la cadena de Spring Security (que no pasan por el
 * {@code @ControllerAdvice}): sin autenticación válida → 401; autenticado sin permiso → 403.
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
        ErrorResponse body = new ErrorResponse(status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), LocalDateTime.now());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
