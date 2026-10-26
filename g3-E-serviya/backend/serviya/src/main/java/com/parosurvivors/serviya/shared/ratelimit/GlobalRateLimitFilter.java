package com.parosurvivors.serviya.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Nivel 1: techo global de la API. Aplica la política {@code api-default} (token bucket, modo OPEN) a todo
 * {@code /api/v1/**}, por usuario autenticado o por IP si es anónimo ({@link RateLimitSubject}). Es la red
 * de seguridad contra scraping y bucles; las cuotas estrictas por endpoint son el nivel 2 (interceptor).
 *
 * <p>Se registra DESPUÉS del filtro JWT ({@code SecurityConfig}) para que el sujeto pueda ser el usuario
 * autenticado. No es {@code @Component}: lo instancia {@code SecurityConfig}, como los demás filtros de la
 * cadena de seguridad.</p>
 */
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private static final String POLICY = "api-default";

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    /** Solo la API de negocio; deja fuera actuator, swagger y estáticos. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RateLimitSubject subject = RateLimitSubject.of(request);
        RateLimitDecision decision = rateLimiter.tryConsume(POLICY, subject.type(), subject.id());
        if (!decision.allowed()) {
            RateLimitHttp.writeTooManyRequests(request, response, decision.retryAfterSeconds(), objectMapper);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
