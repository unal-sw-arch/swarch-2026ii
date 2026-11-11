package com.parosurvivors.gateway.ratelimit;

import com.parosurvivors.gateway.error.GatewayErrorCode;
import com.parosurvivors.gateway.error.GatewayErrorWriter;
import com.parosurvivors.gateway.ratelimit.GatewayRateLimitProperties.SlidingPolicy;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Cuota de auth por IP en el borde (T4.9/P3.7): aplica la política de ventana deslizante que corresponda a
 * cada endpoint público de {@code /api/v1/auth/**} (login/registro/recuperación). Frena la fuerza bruta en
 * la puerta, por la IP real del cliente (el gateway es el borde). Reemplaza al {@code RateLimitingFilter}
 * del monolito. Al superar la cuota: 429 con el cuerpo 4.4 ({@code RATE_LIMITED}) y {@code Retry-After}.
 *
 * <p>Orden -1: después de Security pero antes del techo global ({@code GlobalRateLimitWebFilter}, orden 0),
 * para cortar un flood de login antes de consumir el balde global. La cuota por CORREO de recuperación
 * ({@code reset-email}) sigue en el monolito (es domain-aware, necesita el cuerpo parseado).</p>
 */
@Component
@Order(-1)
public class AuthRateLimitWebFilter implements WebFilter {

    /** Ruta exacta → nombre de política en {@code serviya.ratelimit.auth}. Lo demás no lleva cuota por IP aquí. */
    private static final Map<String, String> POLICY_BY_PATH = Map.of(
            "/api/v1/auth/login", "login-ip",
            "/api/v1/auth/register", "register-ip",
            "/api/v1/auth/password-reset", "reset-ip",
            "/api/v1/auth/password-reset/validate", "reset-validate-ip",
            "/api/v1/auth/password-reset/confirm", "reset-confirm-ip");

    private final ReactiveRedisRateLimiter rateLimiter;
    private final GatewayRateLimitProperties properties;
    private final GatewayErrorWriter errorWriter;

    public AuthRateLimitWebFilter(ReactiveRedisRateLimiter rateLimiter,
                                  GatewayRateLimitProperties properties,
                                  GatewayErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String policyName = POLICY_BY_PATH.get(exchange.getRequest().getPath().value());
        if (policyName == null) {
            return chain.filter(exchange); // no es un endpoint con cuota de auth por IP
        }
        SlidingPolicy policy = properties.getAuth().get(policyName);
        if (policy == null || policy.getWindow() == null) {
            return chain.filter(exchange); // política no configurada: no bloquear
        }
        return rateLimiter.tryConsumeSliding(policyName, "ip", clientIp(exchange),
                        policy.getCapacity(), policy.getWindow().toMillis(), policy.isFailOpen())
                .flatMap(decision -> decision.allowed() ? chain.filter(exchange) : reject(exchange, decision));
    }

    private Mono<Void> reject(ServerWebExchange exchange, RateLimitDecision decision) {
        exchange.getResponse().getHeaders()
                .set(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.RATE_LIMITED,
                "Has superado el límite de intentos; reinténtalo más tarde.", null);
    }

    private static String clientIp(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return (remote != null && remote.getAddress() != null)
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}
