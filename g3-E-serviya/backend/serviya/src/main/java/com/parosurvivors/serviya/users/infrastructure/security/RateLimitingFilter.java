package com.parosurvivors.serviya.users.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitDecision;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitHttp;
import com.parosurvivors.serviya.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica la cuota POR IP a los endpoints publicos de {@code /api/v1/auth/**} antes de que la peticion
 * llegue a ningun sitio. Usa el {@link RateLimiter} distribuido (redis-coord), de modo que el limite es
 * el mismo con ≥2 instancias.
 *
 * <p>Va delante del filtro JWT a proposito: frena al atacante en la puerta, sin gastar ciclos validando
 * tokens ni consultando la base de datos en peticiones que van a rechazarse igualmente. Como estos
 * endpoints son publicos y no hay usuario autenticado del que tirar, la IP es el unico sujeto disponible
 * aqui (D-09); la cuota por CORREO de recuperacion se aplica en el controlador, donde el cuerpo ya esta
 * parseado (leerlo dentro del filtro consumiria el InputStream y romperia la lectura posterior).</p>
 *
 * <p>No es un {@code @Component}: lo instancia {@code SecurityConfig}, igual que
 * {@link JwtAuthenticationFilter}, para que no se auto-registre fuera de la cadena de Spring Security.</p>
 */
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    /** Ruta -> politica aplicable (nombre en {@code app.ratelimit.policies}). Lo demas no lleva cuota por IP. */
    private static final Map<String, String> POLICY_BY_PATH = Map.of(
            "/api/v1/auth/login", "login-ip",
            "/api/v1/auth/register", "register-ip",
            "/api/v1/auth/password-reset", "reset-ip",
            "/api/v1/auth/password-reset/validate", "reset-validate-ip",
            "/api/v1/auth/password-reset/confirm", "reset-confirm-ip");

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    /** Solo se ejecuta en /api/v1/auth/**; el resto de la API ni siquiera lo atraviesa. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String policy = POLICY_BY_PATH.get(request.getRequestURI());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = rateLimiter.tryConsume(policy, "ip", clientIp(request));
        if (!decision.allowed()) {
            // Estamos ANTES del DispatcherServlet: el @RestControllerAdvice no ve esto, asi que la
            // respuesta se escribe aqui con el mismo formato (igual que RestAccessErrorHandler).
            RateLimitHttp.writeTooManyRequests(request, response, decision.retryAfterSeconds(), objectMapper);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * IP del cliente. Se confia en {@code request.getRemoteAddr()}: con {@code server.forward-headers-strategy}
     * activo, el contenedor ya resuelve la IP real del cliente a partir de {@code X-Forwarded-For} PERO solo
     * cuando el salto inmediato es un proxy de confianza. Deliberadamente NO se parsea {@code X-Forwarded-For}
     * a mano: hacerlo deja que un cliente con el backend expuesto directo se falsifique un balde nuevo por
     * peticion (D-09). En produccion hay que declarar los proxies/balanceadores de confianza
     * ({@code server.tomcat.remoteip.*}) si no estan en un rango privado.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
