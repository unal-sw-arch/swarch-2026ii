package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.CoordinationAvailabilityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Comprueba la disponibilidad de {@code redis-coord} con un {@code PING}, cacheando el resultado unos
 * segundos para no añadir una ida y vuelta a Redis por cada correo. Ante cualquier error devuelve
 * {@code false} (se asume caído). Se conecta con un usuario ACL propio ({@code svc-notifications-coord})
 * que SOLO puede hacer PING: el servicio no toca ninguna clave de Redis.
 *
 * <p>Solo existe con {@code serviya.coordination.enabled=true}; sin el bean, el servicio asume que la
 * coordinación está disponible (p. ej. en desarrollo sin Redis).</p>
 */
@Component
@ConditionalOnProperty(prefix = "serviya.coordination", name = "enabled", havingValue = "true")
public class RedisCoordinationAvailabilityAdapter implements CoordinationAvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(RedisCoordinationAvailabilityAdapter.class);

    private final RedisConnectionFactory factory;
    private final long cacheMillis;

    private volatile long lastCheckAt = 0L;
    private volatile boolean lastResult = true;

    public RedisCoordinationAvailabilityAdapter(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${serviya.coordination.probe-cache-ms:5000}") long cacheMillis) {
        this.factory = redisConnectionFactory;
        this.cacheMillis = cacheMillis;
    }

    @Override
    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < cacheMillis) {
            return lastResult;
        }
        boolean available;
        try (RedisConnection conn = factory.getConnection()) {
            available = "PONG".equalsIgnoreCase(conn.ping());
        } catch (RuntimeException ex) {
            log.debug("Sondeo de redis-coord falló; se considera no disponible", ex);
            available = false;
        }
        lastResult = available;
        lastCheckAt = now;
        return available;
    }
}
