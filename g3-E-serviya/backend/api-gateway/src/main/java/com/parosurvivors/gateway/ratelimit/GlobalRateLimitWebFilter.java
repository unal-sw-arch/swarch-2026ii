package com.parosurvivors.gateway.ratelimit;

import com.parosurvivors.gateway.error.GatewayErrorCode;
import com.parosurvivors.gateway.error.GatewayErrorWriter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Techo global de rate limiting del gateway (T3.9): aplica la política {@code api-default} a todo
 * {@code /api/v1/**}, por usuario autenticado (sujeto {@code user:<sub>} del JWT ya validado) o por IP si es
 * anónimo. Al superar la cuota responde 429 con el cuerpo 4.4 ({@link GatewayErrorCode#RATE_LIMITED}) y la
 * cabecera {@code Retry-After}.
 *
 * <p>Orden por defecto (0): corre <b>después</b> de la cadena de Spring Security (orden -100), de modo que
 * el sujeto pueda ser el usuario autenticado. Las cuotas estrictas por endpoint siguen en los servicios
 * (son domain-aware); esto es solo la red de seguridad contra scraping y bucles.</p>
 */
@Component
@Order(0)
public class GlobalRateLimitWebFilter implements WebFilter {

    private static final String API_PREFIX = "/api/v1/";
    static final String SUBJECT_USER = "user";
    static final String SUBJECT_IP = "ip";

    private final ReactiveRedisRateLimiter rateLimiter;
    private final GatewayErrorWriter errorWriter;

    public GlobalRateLimitWebFilter(ReactiveRedisRateLimiter rateLimiter, GatewayErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(API_PREFIX)) {
            return chain.filter(exchange); // actuator/estáticos fuera del techo
        }
        return subject(exchange).flatMap(subject -> rateLimiter.tryConsume(subject.type(), subject.id())
                .flatMap(decision -> decision.allowed()
                        ? chain.filter(exchange)
                        : reject(exchange, decision)));
    }

    private Mono<Void> reject(ServerWebExchange exchange, RateLimitDecision decision) {
        exchange.getResponse().getHeaders()
                .set(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.RATE_LIMITED,
                "Has superado el límite de peticiones; reinténtalo más tarde.", null);
    }

    /** Sujeto de la cuota: {@code user:<sub>} si hay un principal autenticado, si no {@code ip:<addr>}. */
    private Mono<Subject> subject(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(GlobalRateLimitWebFilter::isAuthenticated)
                .map(auth -> new Subject(SUBJECT_USER, auth.getName()))
                .defaultIfEmpty(new Subject(SUBJECT_IP, clientIp(exchange)));
    }

    private static boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null;
    }

    private static String clientIp(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return (remote != null && remote.getAddress() != null)
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }

    private record Subject(String type, String id) {
    }
}
