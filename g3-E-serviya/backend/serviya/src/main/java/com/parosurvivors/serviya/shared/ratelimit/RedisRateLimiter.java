package com.parosurvivors.serviya.shared.ratelimit;

import com.parosurvivors.serviya.shared.ratelimit.RateLimitProperties.FailureMode;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitProperties.Policy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link RateLimiter} con algoritmos atómicos en Lua sobre {@code redis-coord}
 * (convención §3 de la guía). Soporta token bucket (techo global) y ventana deslizante (auth/costosos).
 *
 * <p>Modo de fallo por política (D-07): ante un error de Redis, {@code OPEN} permite y {@code CLOSED}
 * rechaza; ambos emiten la métrica {@code ratelimit.decision{result=error_open|error_closed}}.</p>
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /** Espera sugerida cuando se rechaza por caída de Redis (CLOSED): corta, es un fallo transitorio. */
    private static final Duration ERROR_CLOSED_RETRY = Duration.ofSeconds(5);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET =
            RedisScript.of(new ClassPathResource("redis-scripts/ratelimit_token_bucket.lua"), List.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SLIDING_WINDOW =
            RedisScript.of(new ClassPathResource("redis-scripts/ratelimit_sliding_window.lua"), List.class);

    /** Nombre de la instancia de circuit breaker compartida con el lock (misma instancia redis-coord). */
    public static final String BREAKER = "redis-coord";

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker breaker;

    public RedisRateLimiter(@Qualifier("coordHotRedisTemplate") StringRedisTemplate coordHotRedisTemplate,
                            RateLimitProperties props,
                            MeterRegistry meterRegistry,
                            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redis = coordHotRedisTemplate;
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.breaker = circuitBreakerRegistry.circuitBreaker(BREAKER);
    }

    /** Clave de la cuota: {@code rl:v1:{<tipo>:<id>}:<politica>} (hash tag para Cluster, versión para migrar). */
    public static String key(String subjectType, String subjectId, String policy) {
        return "rl:v1:{" + subjectType + ":" + subjectId + "}:" + policy;
    }

    @Override
    public RateLimitDecision tryConsume(String policy, String subjectType, String subjectId, int tokens) {
        if (!props.isEnabled()) {
            return RateLimitDecision.allowed(Long.MAX_VALUE);
        }
        Policy p = props.policy(policy);
        if (p == null) {
            log.warn("Política de rate limit '{}' no configurada; se permite la petición", policy);
            return RateLimitDecision.allowed(Long.MAX_VALUE);
        }

        String key = key(subjectType, subjectId, policy);
        try {
            // El breaker (Fase 4) corta rápido si redis-coord está caído/lento: al estar ABIERTO lanza
            // CallNotPermittedException (RuntimeException) y se aplica el modo de fallo sin esperar el timeout.
            List<Long> r = breaker.executeSupplier(() -> evaluate(p, key, tokens));
            boolean allowed = !r.isEmpty() && r.get(0) != null && r.get(0) == 1L;
            long remaining = r.size() > 1 && r.get(1) != null ? r.get(1) : 0L;
            long retryMs = r.size() > 2 && r.get(2) != null ? r.get(2) : 0L;

            metric(policy, allowed ? "allowed" : "rejected");
            return allowed
                    ? RateLimitDecision.allowed(remaining)
                    : RateLimitDecision.rejected(remaining, Duration.ofMillis(retryMs));
        } catch (RuntimeException ex) {
            return onRedisFailure(policy, p, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> evaluate(Policy p, String key, int tokens) {
        return switch (p.getAlgorithm()) {
            case TOKEN_BUCKET -> redis.execute(TOKEN_BUCKET, List.of(key),
                    Long.toString(p.getCapacity()),
                    Double.toString(p.getRefillPerSecond()),
                    Integer.toString(tokens));
            case SLIDING_WINDOW -> redis.execute(SLIDING_WINDOW, List.of(key),
                    Long.toString(p.getCapacity()),
                    Long.toString(p.getWindow().toMillis()),
                    Integer.toString(tokens),
                    UUID.randomUUID().toString());
        };
    }

    private RateLimitDecision onRedisFailure(String policy, Policy p, RuntimeException ex) {
        if (p.getFailureMode() == FailureMode.CLOSED) {
            log.error("Rate limiter sin Redis; política '{}' es CLOSED → se rechaza la petición", policy, ex);
            metric(policy, "error_closed");
            return RateLimitDecision.rejected(0L, ERROR_CLOSED_RETRY);
        }
        log.error("Rate limiter sin Redis; política '{}' es OPEN → se deja pasar la petición", policy, ex);
        metric(policy, "error_open");
        return RateLimitDecision.allowed(0L);
    }

    private void metric(String policy, String result) {
        Counter.builder("ratelimit.decision")
                .tag("policy", policy)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
