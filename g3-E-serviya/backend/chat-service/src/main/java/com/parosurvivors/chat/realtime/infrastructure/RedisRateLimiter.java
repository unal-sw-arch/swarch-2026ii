package com.parosurvivors.chat.realtime.infrastructure;

import com.parosurvivors.chat.shared.application.ports.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Límite de envío por usuario (RNF-CHAT-12) en redis-coord: {@code chat:rl:{user}:{ventana}} con TTL.
 * <b>Fail-open controlado</b> (§5.5): si Redis falla o está lleno, cae a un limitador en memoria POR INSTANCIA
 * y registra la degradación; nunca se bloquea el envío de mensajes por una caída de Redis.
 *
 * <p>La clave se crea con {@code SET NX EX} y luego {@code INCR}: así siempre tiene TTL (el usuario ACL del chat
 * no puede ejecutar scripts, y un INCR + EXPIRE separados dejarían una clave inmortal si el proceso muere en medio).</p>
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int maxMessages;
    private final int windowSeconds;
    private final InMemoryFallback fallback;

    RedisRateLimiter(@Qualifier("coordRedis") StringRedisTemplate redis, Clock clock,
                     @Value("${serviya.chat.rate-limit.max-messages:20}") int maxMessages,
                     @Value("${serviya.chat.rate-limit.window-seconds:10}") int windowSeconds) {
        this.redis = redis;
        this.clock = clock;
        this.maxMessages = maxMessages;
        this.windowSeconds = windowSeconds;
        this.fallback = new InMemoryFallback(clock, maxMessages, windowSeconds);
    }

    @Override
    public boolean tryAcquire(long userId) {
        try {
            long window = clock.millis() / 1000 / windowSeconds;
            String key = "chat:rl:" + userId + ":" + window;
            redis.opsForValue().setIfAbsent(key, "0", Duration.ofSeconds(windowSeconds * 2L));
            Long count = redis.opsForValue().increment(key);
            return count != null && count <= maxMessages;
        } catch (RuntimeException e) {
            log.warn("Rate limiting en Redis no disponible; se usa el limitador en memoria: {}", e.toString());
            return fallback.tryAcquire(userId);
        }
    }

    /** Ventana fija en memoria. Por instancia: con N instancias el límite efectivo puede ser hasta N veces mayor. */
    static final class InMemoryFallback {
        private record Window(long id, int count) {
        }

        private final Map<Long, Window> windows = new ConcurrentHashMap<>();
        private final Clock clock;
        private final int max;
        private final int windowSeconds;

        InMemoryFallback(Clock clock, int max, int windowSeconds) {
            this.clock = clock;
            this.max = max;
            this.windowSeconds = windowSeconds;
        }

        boolean tryAcquire(long userId) {
            long current = clock.millis() / 1000 / windowSeconds;
            if (windows.size() > 10_000) {
                windows.values().removeIf(w -> w.id() < current);
            }
            Window w = windows.merge(userId, new Window(current, 1),
                    (old, fresh) -> old.id() == current ? new Window(current, old.count() + 1) : fresh);
            return w.count() <= max;
        }
    }
}
