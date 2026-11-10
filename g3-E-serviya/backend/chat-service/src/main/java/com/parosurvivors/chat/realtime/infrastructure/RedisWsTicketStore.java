package com.parosurvivors.chat.realtime.infrastructure;

import com.parosurvivors.chat.realtime.application.ports.TicketStoreUnavailableException;
import com.parosurvivors.chat.realtime.application.ports.WsTicketStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Tickets en redis-coord (instancia A): {@code chat:wsticket:{ticket}} con TTL de ~30 s; consumo de un solo uso. */
@Component
class RedisWsTicketStore implements WsTicketStore {

    static final String PREFIX = "chat:wsticket:";
    private static final int TTL_SECONDS = 30;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    RedisWsTicketStore(@Qualifier("coordRedis") StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(PREFIX + ticket, Long.toString(userId), Duration.ofSeconds(TTL_SECONDS));
        } catch (RuntimeException e) { // Redis caído o lleno (noeviction)
            throw new TicketStoreUnavailableException(e);
        }
        return ticket;
    }

    @Override
    public Optional<Long> consume(String ticket) {
        try {
            // El usuario ACL `chat` no tiene GETDEL: GET y luego DEL. Sigue siendo de un solo uso porque SOLO gana
            // quien consigue borrar la clave (DEL devuelve true una única vez aunque dos conexiones lean a la vez).
            String key = PREFIX + ticket;
            String value = redis.opsForValue().get(key);
            if (value == null || !Boolean.TRUE.equals(redis.delete(key))) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (RuntimeException e) {
            throw new TicketStoreUnavailableException(e);
        }
    }

    @Override
    public int ttlSeconds() {
        return TTL_SECONDS;
    }
}
