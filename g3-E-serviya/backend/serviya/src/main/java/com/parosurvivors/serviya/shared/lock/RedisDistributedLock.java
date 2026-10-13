package com.parosurvivors.serviya.shared.lock;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link DistributedLock} con protocolo propio {@code SET NX PX} + Lua sobre
 * {@code redis-coord} (ADR-003 / D-04). Los scripts viven en el classpath ({@code redis-scripts/*.lua})
 * y se ejecutan con {@code EVALSHA} vía {@link RedisScript} (maneja {@code SCRIPT LOAD}/{@code NOSCRIPT}).
 *
 * <p>Fallo cerrado (D-08): cualquier error de Redis al adquirir se traga, se registra y se devuelve vacío,
 * de modo que la sección crítica no se ejecuta.</p>
 */
@Component
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    private static final RedisScript<Long> ACQUIRE =
            RedisScript.of(new ClassPathResource("redis-scripts/lock_acquire.lua"), Long.class);
    private static final RedisScript<Long> ACQUIRE_FENCED =
            RedisScript.of(new ClassPathResource("redis-scripts/lock_acquire_fenced.lua"), Long.class);
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(new ClassPathResource("redis-scripts/lock_release.lua"), Long.class);
    private static final RedisScript<Long> RENEW =
            RedisScript.of(new ClassPathResource("redis-scripts/lock_renew.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final CircuitBreaker breaker;
    private final MeterRegistry meterRegistry;

    public RedisDistributedLock(@Qualifier("coordRedisTemplate") StringRedisTemplate coordRedisTemplate,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                MeterRegistry meterRegistry) {
        this.redis = coordRedisTemplate;
        // Misma instancia de breaker que el rate limiter ("redis-coord", config en resilience4j.* del YAML):
        // si redis-coord falla, lock y limiter abren juntos. El nombre se mantiene en sync con RedisRateLimiter.BREAKER.
        this.breaker = circuitBreakerRegistry.circuitBreaker("redis-coord");
        this.meterRegistry = meterRegistry;
    }

    /** Métrica de locks (Fase 5): p. ej. redis.lock.renew{result=lost} alimenta la alerta de locks perdidos. */
    private void metric(String name, String resource, String result) {
        Counter.builder(name)
                .tag("resource", resource)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    /** Clave del lock para un recurso: {@code lock:{<recurso>}} (el hash tag lo mantiene cluster-safe). */
    public static String lockKey(String resource) {
        return "lock:{" + resource + "}";
    }

    /** Clave del contador de fencing: {@code fence:{<recurso>}} (mismo hash tag que el lock). */
    public static String fenceKey(String resource) {
        return "fence:{" + resource + "}";
    }

    @Override
    public Optional<LockHandle> tryAcquire(String resource, Duration ttl) {
        String token = UUID.randomUUID().toString();
        String ttlMillis = Long.toString(ttl.toMillis());
        try {
            Long result = breaker.executeSupplier(
                    () -> redis.execute(ACQUIRE, List.of(lockKey(resource)), token, ttlMillis));
            if (result != null && result == 1L) {
                metric("redis.lock.acquire", resource, "acquired");
                return Optional.of(new RedisLockHandle(resource, token, OptionalLong.empty()));
            }
            metric("redis.lock.acquire", resource, "busy");
            log.trace("Lock '{}' ocupado; otra instancia lo tiene", resource);
            return Optional.empty();
        } catch (RuntimeException ex) {
            // Fallo cerrado (D-08): sin coordinación no ejecutamos la sección crítica.
            metric("redis.lock.acquire", resource, "error");
            log.error("No se pudo adquirir el lock '{}' (Redis no respondió); se omite la operación", resource, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<LockHandle> tryAcquireFenced(String resource, Duration ttl) {
        String token = UUID.randomUUID().toString();
        String ttlMillis = Long.toString(ttl.toMillis());
        try {
            Long fencing = breaker.executeSupplier(
                    () -> redis.execute(ACQUIRE_FENCED, List.of(lockKey(resource), fenceKey(resource)), token, ttlMillis));
            if (fencing != null && fencing > 0L) {
                metric("redis.lock.acquire", resource, "acquired");
                return Optional.of(new RedisLockHandle(resource, token, OptionalLong.of(fencing)));
            }
            metric("redis.lock.acquire", resource, "busy");
            log.trace("Lock '{}' ocupado; otra instancia lo tiene", resource);
            return Optional.empty();
        } catch (RuntimeException ex) {
            metric("redis.lock.acquire", resource, "error");
            log.error("No se pudo adquirir el lock '{}' (Redis no respondió); se omite la operación", resource, ex);
            return Optional.empty();
        }
    }

    private boolean renew(String resource, String token, Duration ttl) {
        try {
            Long result = breaker.executeSupplier(
                    () -> redis.execute(RENEW, List.of(lockKey(resource)), token, Long.toString(ttl.toMillis())));
            boolean renewed = result != null && result == 1L;
            metric("redis.lock.renew", resource, renewed ? "ok" : "lost");
            return renewed;
        } catch (RuntimeException ex) {
            metric("redis.lock.renew", resource, "error");
            log.warn("Error renovando el lock '{}'; se considera perdido", resource, ex);
            return false;
        }
    }

    private void release(String resource, String token) {
        try {
            Long result = breaker.executeSupplier(
                    () -> redis.execute(RELEASE, List.of(lockKey(resource)), token));
            if (result != null && result == 1L) {
                // Flujo normal: era nuestro y lo liberamos explícitamente.
                metric("redis.lock.release", resource, "released");
            } else {
                // No era nuestro: ya expiró o lo tomó otra instancia. Normal con TTL; no es un error.
                metric("redis.lock.release", resource, "lost");
                log.debug("Al liberar, el lock '{}' ya no pertenecía a este token (expiró o lo tomó otro)", resource);
            }
        } catch (RuntimeException ex) {
            metric("redis.lock.release", resource, "error");
            log.warn("Error liberando el lock '{}'; expirará solo por TTL", resource, ex);
        }
    }

    /** Handle concreto: delega renew/release en el lock externo usando su propio token. */
    private final class RedisLockHandle implements LockHandle {
        private final String resource;
        private final String token;
        private final OptionalLong fencingToken;

        private RedisLockHandle(String resource, String token, OptionalLong fencingToken) {
            this.resource = resource;
            this.token = token;
            this.fencingToken = fencingToken;
        }

        @Override
        public String resource() {
            return resource;
        }

        @Override
        public String token() {
            return token;
        }

        @Override
        public OptionalLong fencingToken() {
            return fencingToken;
        }

        @Override
        public boolean renew(Duration ttl) {
            return RedisDistributedLock.this.renew(resource, token, ttl);
        }

        @Override
        public void close() {
            RedisDistributedLock.this.release(resource, token);
        }
    }
}
