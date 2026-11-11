package com.parosurvivors.chat.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.chat.integration.IntegrationTestBase;
import com.parosurvivors.chat.realtime.application.ports.TicketStoreUnavailableException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Adaptadores sobre redis-coord con el ACL REAL del usuario {@code chat} (guía §5.5 y §14: «toda clave del chat
 * tiene prefijo chat: y TTL», degradación con Redis caído).
 */
class RedisAdaptersIntegrationTest extends IntegrationTestBase {

    @Autowired @Qualifier("coordRedis") StringRedisTemplate coordRedis;
    @Autowired RedisWsTicketStore tickets;

    private static StringRedisTemplate unreachableRedis() {
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build());
        f.afterPropertiesSet();
        return new StringRedisTemplate(f);
    }

    @Test
    void el_ticket_se_guarda_con_ttl_y_es_de_un_solo_uso() {
        String ticket = tickets.issue(42);

        Long ttl = coordRedis.getExpire(RedisWsTicketStore.PREFIX + ticket);
        assertThat(ttl).isBetween(1L, 30L);
        assertThat(tickets.consume(ticket)).contains(42L);
        assertThat(tickets.consume(ticket)).isEmpty();
        assertThat(tickets.consume("inexistente")).isEmpty();
    }

    @Test
    void consumir_el_mismo_ticket_a_la_vez_desde_varias_conexiones_deja_un_solo_ganador() throws Exception {
        String ticket = tickets.issue(42);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        var go = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<java.util.concurrent.Future<java.util.Optional<Long>>>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return tickets.consume(ticket);
            }));
        }
        go.countDown();
        long winners = 0;
        for (var f : results) {
            if (f.get(10, java.util.concurrent.TimeUnit.SECONDS).isPresent()) {
                winners++;
            }
        }
        pool.shutdown();
        assertThat(winners).isEqualTo(1);
    }

    @Test
    void sin_redis_los_tickets_responden_no_disponible() {
        RedisWsTicketStore down = new RedisWsTicketStore(unreachableRedis());
        assertThatThrownBy(() -> down.issue(42)).isInstanceOf(TicketStoreUnavailableException.class);
        assertThatThrownBy(() -> down.consume("x")).isInstanceOf(TicketStoreUnavailableException.class);
    }

    @Test
    void el_rate_limit_corta_al_superar_el_umbral_y_su_clave_tiene_ttl() {
        RedisRateLimiter limiter = new RedisRateLimiter(coordRedis, Clock.systemUTC(), 3, 10);
        long user = 777_000 + System.nanoTime() % 1000;

        assertThat(limiter.tryAcquire(user)).isTrue();
        assertThat(limiter.tryAcquire(user)).isTrue();
        assertThat(limiter.tryAcquire(user)).isTrue();
        assertThat(limiter.tryAcquire(user)).isFalse();

        long window = System.currentTimeMillis() / 1000 / 10;
        Long ttl = coordRedis.getExpire("chat:rl:" + user + ":" + window);
        assertThat(ttl).isPositive();
    }

    @Test
    void con_redis_caido_el_rate_limit_pasa_al_limitador_en_memoria() {
        RedisRateLimiter limiter = new RedisRateLimiter(unreachableRedis(), Clock.systemUTC(), 2, 10);
        assertThat(limiter.tryAcquire(1)).isTrue();
        assertThat(limiter.tryAcquire(1)).isTrue();
        assertThat(limiter.tryAcquire(1)).isFalse(); // sigue limitando, ahora en memoria
        assertThat(limiter.tryAcquire(2)).isTrue();
    }

    @Test
    void el_acl_del_chat_no_permite_claves_ajenas_ni_comandos_peligrosos() {
        assertThatThrownBy(() -> coordRedis.opsForValue().set("lock:otro-modulo", "x")).hasStackTraceContaining("NOPERM");
        assertThatThrownBy(() -> coordRedis.keys("*")).hasStackTraceContaining("NOPERM");
        assertThatThrownBy(() -> coordRedis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        })).hasStackTraceContaining("NOPERM");
        assertThatThrownBy(() -> coordRedis.convertAndSend("chat:user:1", "x")).hasStackTraceContaining("NOPERM");
    }
}
