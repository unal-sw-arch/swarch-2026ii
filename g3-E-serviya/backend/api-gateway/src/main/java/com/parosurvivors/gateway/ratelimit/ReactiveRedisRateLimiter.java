package com.parosurvivors.gateway.ratelimit;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Rate limiting distribuido del gateway sobre {@code redis-coord} con los mismos scripts Lua atómicos y las
 * mismas claves {@code rl:v1:{<tipo>:<id>}:<politica>} que el monolito (D-04, sin dos sistemas):
 * <ul>
 *   <li><b>Techo global</b> {@code api-default} (token bucket, modo OPEN), T3.9.</li>
 *   <li><b>Cuotas de auth por IP</b> (ventana deslizante): {@code login-ip}, {@code register-ip},
 *       {@code reset-*}. Portadas del {@code RateLimitingFilter} del monolito (T4.9/P3.7): el gateway ve la
 *       IP real del cliente en el borde, así que es el sitio correcto.</li>
 * </ul>
 *
 * <p>Reactivo (regla #7) y tras el mismo {@link CircuitBreaker} {@code redis-coord} que el {@code min_iat}.
 * Modo de fallo por llamada (D-07): OPEN deja pasar, CLOSED rechaza; ambos emiten métrica de alerta.</p>
 */
@Component
public class ReactiveRedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRedisRateLimiter.class);
    private static final String API_DEFAULT = "api-default";
    private static final String METRIC = "gateway.ratelimit.decision";
    /** Espera sugerida al rechazar por caída de Redis en una política CLOSED (fallo transitorio). */
    private static final long ERROR_CLOSED_RETRY_SECONDS = 5L;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET =
            RedisScript.of(new ClassPathResource("redis-scripts/ratelimit_token_bucket.lua"), List.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SLIDING_WINDOW =
            RedisScript.of(new ClassPathResource("redis-scripts/ratelimit_sliding_window.lua"), List.class);

    private final ReactiveStringRedisTemplate redis;
    private final CircuitBreaker breaker;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final long apiDefaultCapacity;
    private final double apiDefaultRefillPerSecond;

    public ReactiveRedisRateLimiter(
            ReactiveStringRedisTemplate redis,
            CircuitBreaker redisCoordCircuitBreaker,
            MeterRegistry meters,
            @Value("${serviya.ratelimit.enabled:true}") boolean enabled,
            @Value("${serviya.ratelimit.api-default.capacity:200}") long apiDefaultCapacity,
            @Value("${serviya.ratelimit.api-default.refill-per-second:5}") double apiDefaultRefillPerSecond) {
        this.redis = redis;
        this.breaker = redisCoordCircuitBreaker;
        this.meters = meters;
        this.enabled = enabled;
        this.apiDefaultCapacity = apiDefaultCapacity;
        this.apiDefaultRefillPerSecond = apiDefaultRefillPerSecond;
    }

    public static String key(String subjectType, String subjectId, String policy) {
        return "rl:v1:{" + subjectType + ":" + subjectId + "}:" + policy;
    }

    /** Techo global {@code api-default} (token bucket, OPEN). */
    public Mono<RateLimitDecision> tryConsume(String subjectType, String subjectId) {
        if (!enabled) {
            return Mono.just(RateLimitDecision.allow());
        }
        List<String> args = List.of(Long.toString(apiDefaultCapacity),
                Double.toString(apiDefaultRefillPerSecond), "1");
        return run(API_DEFAULT, TOKEN_BUCKET, key(subjectType, subjectId, API_DEFAULT), args, true);
    }

    /**
     * Política de ventana deslizante (cuotas de auth por IP). {@code failOpen=false} ⇒ CLOSED: si Redis cae,
     * rechaza (abuso/coste; registro/reset). {@code true} ⇒ OPEN: deja pasar (disponibilidad; login).
     */
    public Mono<RateLimitDecision> tryConsumeSliding(String policy, String subjectType, String subjectId,
                                                     long capacity, long windowMs, boolean failOpen) {
        if (!enabled) {
            return Mono.just(RateLimitDecision.allow());
        }
        List<String> args = List.of(Long.toString(capacity), Long.toString(windowMs), "1",
                UUID.randomUUID().toString());
        return run(policy, SLIDING_WINDOW, key(subjectType, subjectId, policy), args, failOpen);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<RateLimitDecision> run(String policy, RedisScript<List> script, String key,
                                        List<String> args, boolean failOpen) {
        return Mono.defer(() -> redis.execute(script, List.of(key), args).next())
                .transformDeferred(CircuitBreakerOperator.of(breaker))
                .map(result -> toDecision(policy, result))
                .onErrorResume(ex -> {
                    count(policy, ex instanceof CallNotPermittedException ? "breaker-open" : "error");
                    log.warn("Rate limit '{}' sin redis-coord ({}); fail-{}", policy,
                            ex.getClass().getSimpleName(), failOpen ? "open" : "closed");
                    return Mono.just(failOpen
                            ? RateLimitDecision.allow()
                            : RateLimitDecision.rejected(ERROR_CLOSED_RETRY_SECONDS));
                });
    }

    private RateLimitDecision toDecision(String policy, List<?> result) {
        boolean allowed = !result.isEmpty() && asLong(result.get(0)) == 1L;
        long retryMs = result.size() > 2 ? asLong(result.get(2)) : 0L;
        count(policy, allowed ? "allowed" : "rejected");
        if (allowed) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.rejected(Math.max(1L, (long) Math.ceil(retryMs / 1000.0)));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private void count(String policy, String result) {
        meters.counter(METRIC, "policy", policy, "result", result).increment();
    }
}
