package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.users.infrastructure.security.JwtProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Escritura de {@code min_iat} en {@code redis-coord} (contrato 4.3): clave, valor (epoch del corte) y TTL
 * (accessTtl + tolerancia), y que es de mejor esfuerzo (si Redis falla, no propaga).
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenRevocationAdapterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private RedisTokenRevocationAdapter adapter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setAccessTtl(Duration.ofMinutes(15));
        props.setRevocationClockSkew(Duration.ofSeconds(60));
        // Registro real: el breaker cerrado ejecuta el runnable directamente (y relanza si falla).
        adapter = new RedisTokenRevocationAdapter(redis, CircuitBreakerRegistry.ofDefaults(), props);
    }

    @Test
    void revokeAllIssuedBefore_writesKey_valueEpoch_andTtlAccessTtlPlusSkew() {
        when(redis.opsForValue()).thenReturn(valueOps);
        Instant cutoff = Instant.ofEpochSecond(1_762_632_001L);

        adapter.revokeAllIssuedBefore(42L, cutoff);

        // Clave auth:min_iat:{userId}, valor = epoch en segundos del corte, TTL = 15m + 60s = 16m.
        verify(valueOps).set("auth:min_iat:42", "1762632001", Duration.ofMinutes(16));
    }

    @Test
    void revokeAllIssuedBefore_isBestEffort_swallowsRedisErrors() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("redis-coord caido"))
                .when(valueOps).set(anyString(), anyString(), eq(Duration.ofMinutes(16)));

        // No debe propagar: el baneo/eliminacion/etc. no puede fallar porque Redis este caido.
        assertThatCode(() -> adapter.revokeAllIssuedBefore(42L, Instant.now()))
                .doesNotThrowAnyException();

        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void minIatKey_buildsContractKey() {
        assertThat(RedisTokenRevocationAdapter.minIatKey(99L)).isEqualTo("auth:min_iat:99");
    }
}
