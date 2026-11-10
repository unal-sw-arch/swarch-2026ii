package com.parosurvivors.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** Mapeo de la respuesta del token bucket Lua a {@link RateLimitDecision}, y fail-open (OPEN) de la cuota. */
@ExtendWith(MockitoExtension.class)
class ReactiveRedisRateLimiterTest {

    @Mock private ReactiveStringRedisTemplate redis;

    private ReactiveRedisRateLimiter limiter(boolean enabled) {
        return new ReactiveRedisRateLimiter(redis, CircuitBreaker.ofDefaults("test"),
                new SimpleMeterRegistry(), enabled, 200L, 5d);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubScript(Flux result) {
        // Overload execute(script, keys, args-as-List): inequívoco (no varargs).
        doReturn(result).when(redis).execute(any(RedisScript.class), anyList(), anyList());
    }

    @Test
    void allows_whenBucketHasTokens() {
        stubScript(Flux.just(List.of(1L, 199L, 0L)));

        StepVerifier.create(limiter(true).tryConsume("ip", "1.2.3.4"))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }

    @Test
    void rejects_whenBucketEmpty_withRetryAfterInSeconds() {
        stubScript(Flux.just(List.of(0L, 0L, 1500L))); // 1500 ms → 2 s

        StepVerifier.create(limiter(true).tryConsume("user", "42"))
                .assertNext(decision -> {
                    assertThat(decision.allowed()).isFalse();
                    assertThat(decision.retryAfterSeconds()).isEqualTo(2L);
                })
                .verifyComplete();
    }

    @Test
    void failsOpen_whenRedisErrors() {
        stubScript(Flux.error(new RuntimeException("redis-coord caído")));

        // api-default es OPEN: ante fallo de Redis se deja pasar.
        StepVerifier.create(limiter(true).tryConsume("ip", "1.2.3.4"))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }

    @Test
    void disabled_allowsWithoutTouchingRedis() {
        StepVerifier.create(limiter(false).tryConsume("ip", "1.2.3.4"))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();

        verifyNoInteractions(redis);
    }

    // --- Ventana deslizante (cuotas de auth por IP) ---

    @Test
    void slidingWindow_allows_whenUnderCapacity() {
        stubScript(Flux.just(List.of(1L, 9L, 0L)));

        StepVerifier.create(limiter(true).tryConsumeSliding("login-ip", "ip", "1.2.3.4", 10, 900_000, true))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }

    @Test
    void slidingWindow_rejects_whenOverCapacity() {
        stubScript(Flux.just(List.of(0L, 0L, 5000L))); // 5000 ms → 5 s

        StepVerifier.create(limiter(true).tryConsumeSliding("login-ip", "ip", "1.2.3.4", 10, 900_000, true))
                .assertNext(decision -> {
                    assertThat(decision.allowed()).isFalse();
                    assertThat(decision.retryAfterSeconds()).isEqualTo(5L);
                })
                .verifyComplete();
    }

    @Test
    void slidingWindow_failClosed_rejectsOnRedisError() {
        stubScript(Flux.error(new RuntimeException("redis-coord caído")));

        // register/reset son CLOSED: ante fallo de Redis se rechaza (abuso/coste).
        StepVerifier.create(limiter(true).tryConsumeSliding("register-ip", "ip", "1.2.3.4", 5, 3_600_000, false))
                .assertNext(decision -> assertThat(decision.allowed()).isFalse())
                .verifyComplete();
    }

    @Test
    void slidingWindow_failOpen_allowsOnRedisError() {
        stubScript(Flux.error(new RuntimeException("redis-coord caído")));

        // login es OPEN: ante fallo de Redis se deja pasar (disponibilidad del acceso).
        StepVerifier.create(limiter(true).tryConsumeSliding("login-ip", "ip", "1.2.3.4", 10, 900_000, true))
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }
}
