package com.parosurvivors.chat.realtime.infrastructure;

import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Un usuario está en línea si algún suscriptor escucha su canal {@code chat:user:{id}}: cada instancia se suscribe
 * al canal de los usuarios que tiene conectados. {@code PUBSUB NUMSUB} lo responde sin guardar nada (no hay claves
 * de presencia que mantener ni desalojar; la presencia con TTL es de la fase 2).
 */
@Component
class RedisOnlineUsers implements OnlineUsers {

    private static final Duration TIMEOUT = Duration.ofMillis(300);

    private final StringRedisTemplate redis;

    RedisOnlineUsers(@Qualifier("pubSubRedis") StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Set<Long> onlineAmong(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(userIds);
        byte[][] channels = ids.stream().map(id -> RealtimeChannels.forUser(id).getBytes(StandardCharsets.UTF_8))
                .toArray(byte[][]::new);
        // PUBSUB NUMSUB devuelve enteros: el execute genérico de Spring Data no los sabe leer, así que se usa la API
        // nativa de Lettuce (que sí tiene pubsubNumsub tipado).
        Map<byte[], Long> counts = redis.execute((RedisCallback<Map<byte[], Long>>) connection -> {
            @SuppressWarnings("unchecked")
            BaseRedisAsyncCommands<byte[], byte[]> commands =
                    (BaseRedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            try {
                return commands.pubsubNumsub(channels).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido consultando PUBSUB NUMSUB", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("PUBSUB NUMSUB falló", e);
            }
        });
        Set<Long> online = new HashSet<>();
        if (counts != null) {
            counts.forEach((channel, subscribers) -> {
                if (subscribers != null && subscribers > 0) {
                    String name = new String(channel, StandardCharsets.UTF_8);
                    online.add(Long.parseLong(name.substring(name.lastIndexOf(':') + 1)));
                }
            });
        }
        return online;
    }
}
