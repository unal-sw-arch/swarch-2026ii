package com.parosurvivors.serviya.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Nivel 2: aplica la cuota estricta de los endpoints anotados con {@link RateLimit}. Corre DESPUÉS de la
 * cadena de seguridad (es un interceptor de Spring MVC), así que el sujeto puede ser el usuario autenticado.
 * Si la cuota está agotada responde 429 y corta la petición antes de llegar al controlador.
 *
 * <p>No es {@code @Component} a propósito: lo construye {@code StaticResourceConfig} a partir del
 * {@link RateLimiter}, para no forzar su carga (ni la del RateLimiter) en los slices {@code @WebMvcTest}.</p>
 */
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        RateLimitSubject subject = RateLimitSubject.of(request);
        RateLimitDecision decision = rateLimiter.tryConsume(annotation.value(), subject.type(), subject.id());
        if (!decision.allowed()) {
            RateLimitHttp.writeTooManyRequests(request, response, decision.retryAfterSeconds(), objectMapper);
            return false;
        }
        return true;
    }
}
