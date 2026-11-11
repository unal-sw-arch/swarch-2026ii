package com.parosurvivors.gateway.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Chequeo de {@code min_iat} (T3.5): rechaza tokens emitidos antes del umbral, deja pasar si no hay umbral
 * o es anterior, y hace <b>fail-open</b> (P3.1) si {@code redis-coord} no responde.
 */
@ExtendWith(MockitoExtension.class)
class MinIatReactiveJwtDecoderTest {

    private static final String TOKEN = "jwt-serializado";
    private static final String KEY = "auth:min_iat:42";

    @Mock private ReactiveJwtDecoder delegate;
    @Mock private ReactiveStringRedisTemplate redis;
    @Mock private ReactiveValueOperations<String, String> valueOps;

    private MinIatReactiveJwtDecoder decoder;
    private SimpleMeterRegistry meters;
    private Instant issuedAt;

    @BeforeEach
    void setUp() {
        // Breaker por defecto (ventana de 100): no abre durante los casos unitarios, actúa como paso directo.
        meters = new SimpleMeterRegistry();
        decoder = new MinIatReactiveJwtDecoder(delegate, redis, meters, CircuitBreaker.ofDefaults("test"));
        issuedAt = Instant.now();
        when(delegate.decode(TOKEN)).thenReturn(Mono.just(jwt(issuedAt, "42")));
    }

    private static Jwt jwt(Instant iat, String subject) {
        Jwt.Builder builder = Jwt.withTokenValue(TOKEN)
                .header("alg", "ES256")
                .claim("roles", List.of("CLIENT"));
        if (subject != null) {
            builder.subject(subject);
        }
        if (iat != null) {
            builder.issuedAt(iat).expiresAt(iat.plusSeconds(900));
        }
        return builder.build();
    }

    @Test
    void allowsToken_whenMinIatIsBeforeIssuedAt() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(Mono.just(Long.toString(issuedAt.getEpochSecond() - 100)));

        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();
    }

    @Test
    void rejectsToken_whenIssuedBeforeMinIat() {
        when(redis.opsForValue()).thenReturn(valueOps);
        // Umbral posterior a la emisión del token ⇒ revocado.
        when(valueOps.get(KEY)).thenReturn(Mono.just(Long.toString(issuedAt.getEpochSecond() + 100)));

        StepVerifier.create(decoder.decode(TOKEN))
                .expectError(JwtValidationException.class)
                .verify();
    }

    @Test
    void allowsToken_whenNoMinIatKey() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();
    }

    @Test
    void failsOpen_whenRedisErrors() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(Mono.error(new RuntimeException("redis-coord caído")));

        // Fail-open (P3.1): el token pasa pese al fallo de Redis.
        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();
    }

    @Test
    void skipsCheck_whenTokenHasNoSubject() {
        when(delegate.decode(TOKEN)).thenReturn(Mono.just(jwt(issuedAt, null)));

        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();
        verify(redis, never()).opsForValue();
    }

    @Test
    void openBreaker_shortCircuits_withoutHittingRedisAgain() {
        // Breaker diminuto: 2 llamadas, >50% error ⇒ abre tras 2 fallos.
        CircuitBreaker tiny = CircuitBreaker.of("tiny", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30)).build());
        MinIatReactiveJwtDecoder decoderWithTinyBreaker =
                new MinIatReactiveJwtDecoder(delegate, redis, new SimpleMeterRegistry(), tiny);
        when(delegate.decode(TOKEN)).thenReturn(Mono.just(jwt(issuedAt, "42")));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(Mono.error(new RuntimeException("redis-coord caído")));

        // 2 fallos (fail-open) que abren el breaker.
        StepVerifier.create(decoderWithTinyBreaker.decode(TOKEN)).expectNextCount(1).verifyComplete();
        StepVerifier.create(decoderWithTinyBreaker.decode(TOKEN)).expectNextCount(1).verifyComplete();
        // 3ª: breaker ABIERTO ⇒ corta sin tocar Redis, sigue fail-open.
        StepVerifier.create(decoderWithTinyBreaker.decode(TOKEN)).expectNextCount(1).verifyComplete();

        // Solo 2 consultas llegaron a Redis; la 3ª la cortó el breaker (Mono.defer no llegó a construirla).
        verify(valueOps, times(2)).get(KEY);
    }

    @Test
    void recordsMinIatLatency() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();

        // T6.2: la latencia del chequeo min_iat se mide (hot-path de todo el tráfico autenticado).
        org.assertj.core.api.Assertions.assertThat(meters.timer("gateway.min_iat.latency").count()).isEqualTo(1L);
    }

    @Test
    void countsDecodeError_whenDelegateFails() {
        // Firma/iss/aud inválidos o JWKS inalcanzable: el decoder delegado lanza y se cuenta como error.
        when(delegate.decode(TOKEN)).thenReturn(Mono.error(new RuntimeException("JWKS inalcanzable")));

        StepVerifier.create(decoder.decode(TOKEN)).expectError(RuntimeException.class).verify();

        org.assertj.core.api.Assertions.assertThat(
                meters.counter("gateway.jwt.decode", "result", "error").count()).isEqualTo(1.0);
    }

    @Test
    void usesContractKey() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(eq("auth:min_iat:42"))).thenReturn(Mono.empty());

        StepVerifier.create(decoder.decode(TOKEN)).expectNextCount(1).verifyComplete();
        verify(valueOps).get("auth:min_iat:42");
    }
}
