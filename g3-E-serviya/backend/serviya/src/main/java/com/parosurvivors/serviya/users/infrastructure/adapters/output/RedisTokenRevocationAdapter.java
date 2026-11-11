package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.TokenRevocationPort;
import com.parosurvivors.serviya.users.infrastructure.security.JwtProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link TokenRevocationPort} sobre {@code redis-coord} (Fase 2, Axis B). Escribe el umbral
 * {@code min_iat} por usuario según el contrato 4.3 del plan de centralización de auth:
 *
 * <pre>
 *   clave:  auth:min_iat:{userId}
 *   valor:  epoch en segundos = corte (ahora + 1 s, lo calcula el servicio de aplicación)
 *   TTL:    accessTtl + tolerancia de reloj  (pasado ese plazo ya no hay tokens anteriores vivos)
 * </pre>
 *
 * <p>Reusa la misma instancia {@code redis-coord} y el <b>mismo circuit breaker Resilience4j</b> que el
 * lock distribuido y el rate limiter (fila 11 del plan; no se inventa resiliencia nueva). El <b>gateway</b>
 * es el único lector (Fase 3); aquí solo se escribe.</p>
 *
 * <p><b>Mejor esfuerzo:</b> si {@code redis-coord} no responde (breaker abierto o error), se registra y se
 * traga la excepción — a diferencia del lock, que falla cerrado (D-08). El motivo: la garantía durable de
 * cierre de sesión es la revocación de familias de refresh en BD (ya aplicada por el servicio llamador);
 * {@code min_iat} solo recorta la ventana del access token corto. Bloquear un baneo porque Redis está caído
 * sería peor que tolerar una ventana ≤ {@code accessTtl} hasta que el token expire por sí mismo.</p>
 */
@Component
public class RedisTokenRevocationAdapter implements TokenRevocationPort {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationAdapter.class);
    private static final String KEY_PREFIX = "auth:min_iat:";

    private final StringRedisTemplate redis;
    private final CircuitBreaker breaker;
    private final JwtProperties jwtProperties;

    public RedisTokenRevocationAdapter(
            @Qualifier("coordRedisTemplate") StringRedisTemplate coordRedisTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            JwtProperties jwtProperties) {
        this.redis = coordRedisTemplate;
        // Mismo breaker "redis-coord" que lock y rate limiter: si la instancia falla, abren juntos.
        this.breaker = circuitBreakerRegistry.circuitBreaker("redis-coord");
        this.jwtProperties = jwtProperties;
    }

    /** Clave {@code min_iat} de un usuario. */
    public static String minIatKey(Long userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public void revokeAllIssuedBefore(Long userId, Instant cutoff) {
        String key = minIatKey(userId);
        String value = Long.toString(cutoff.getEpochSecond());
        Duration ttl = jwtProperties.getAccessTtl().plus(jwtProperties.getRevocationClockSkew());
        try {
            breaker.executeRunnable(() -> redis.opsForValue().set(key, value, ttl));
            log.debug("min_iat escrito para el usuario {} (corte {}, TTL {}s)", userId, value, ttl.getSeconds());
        } catch (RuntimeException ex) {
            // Mejor esfuerzo: la revocación durable (familias de refresh en BD) ya se aplicó; el access corto
            // expirará solo en <= accessTtl. No propagar: no debe tumbar el baneo/eliminación/etc.
            log.error("No se pudo escribir min_iat para el usuario {} (redis-coord no respondió); "
                    + "la revocación inmediata del access se omite, expirará por TTL", userId, ex);
        }
    }
}
