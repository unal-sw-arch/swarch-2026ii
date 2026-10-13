package com.parosurvivors.serviya.shared.lock;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Prueba de exclusión mutua del lock distribuido contra un Redis real y efímero (Testcontainers).
 * Se SALTA (no falla) si no hay Docker, para no romper el baseline en entornos sin Docker. La batería
 * completa de concurrencia/observabilidad llega en la Fase 5; esto es el mínimo del slice (criterios
 * de aceptación de la Fase 2 en la guía).
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisDistributedLockTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    static RedisDistributedLock lock;

    @BeforeAll
    static void setUp() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        lock = new RedisDistributedLock(redis, CircuitBreakerRegistry.ofDefaults(), new SimpleMeterRegistry());
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void soloUnPosesorALaVez() {
        String resource = "test:excl";
        Optional<LockHandle> first = lock.tryAcquire(resource, Duration.ofSeconds(30));
        Optional<LockHandle> second = lock.tryAcquire(resource, Duration.ofSeconds(30));

        assertThat(first).isPresent();
        assertThat(second).isEmpty(); // ocupado

        first.get().close(); // libera
        Optional<LockHandle> third = lock.tryAcquire(resource, Duration.ofSeconds(30));
        assertThat(third).isPresent(); // ya se puede readquirir
        third.get().close();
    }

    @Test
    void bajoConcurrenciaSoloUnoGana() throws InterruptedException {
        String resource = "test:race";
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<LockHandle> held = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                lock.tryAcquire(resource, Duration.ofSeconds(30)).ifPresent(h -> {
                    winners.incrementAndGet();
                    held.add(h); // no liberamos: queremos ver cuántos lo tienen a la vez
                });
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(winners.get()).isEqualTo(1); // exclusión mutua: exactamente uno
        held.forEach(LockHandle::close);
    }

    @Test
    void noSePuedeLiberarUnLockAjeno() {
        String resource = "test:foreign";
        LockHandle mine = lock.tryAcquire(resource, Duration.ofSeconds(60)).orElseThrow();
        String lockKey = RedisDistributedLock.lockKey(resource);

        // Simula que otra instancia tomó el lock (p. ej. tras expirar y readquirir otro token).
        redis.opsForValue().set(lockKey, "otro-token");

        mine.close(); // compare-and-delete: NO debe borrar un lock que ya no es suyo

        assertThat(redis.opsForValue().get(lockKey)).isEqualTo("otro-token");
        redis.delete(lockKey);
    }

    @Test
    void renovarMantieneYDetectaPerdida() {
        String resource = "test:renew";
        LockHandle handle = lock.tryAcquire(resource, Duration.ofMillis(800)).orElseThrow();

        assertThat(handle.renew(Duration.ofSeconds(30))).isTrue(); // sigue siendo dueño

        // Se pierde el lock (lo borramos como si otra instancia lo hubiera tomado tras expirar).
        redis.delete(RedisDistributedLock.lockKey(resource));
        assertThat(handle.renew(Duration.ofSeconds(30))).isFalse(); // detecta la pérdida

        handle.close();
    }

    @Test
    void fencingTokensEstrictamenteCrecientes() {
        String resource = "test:fence";
        LockHandle f1 = lock.tryAcquireFenced(resource, Duration.ofSeconds(60)).orElseThrow();
        assertThat(f1.fencingToken()).isPresent();
        long t1 = f1.fencingToken().getAsLong();
        f1.close();

        LockHandle f2 = lock.tryAcquireFenced(resource, Duration.ofSeconds(60)).orElseThrow();
        long t2 = f2.fencingToken().getAsLong();
        f2.close();

        assertThat(t2).isGreaterThan(t1);

        // La adquisición sin fencing no expone token (los @Scheduled no lo usan).
        LockHandle plain = lock.tryAcquire("test:nofence", Duration.ofSeconds(60)).orElseThrow();
        assertThat(plain.fencingToken()).isEmpty();
        plain.close();
    }

    @Test
    void fallaCerradoSiRedisNoResponde() {
        // Apunta a un puerto sin nada escuchando, con timeouts cortos: debe devolver vacío, no colgarse.
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(300)).build())
                        .build())
                .build();
        LettuceConnectionFactory dead =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6390), clientConfig);
        dead.afterPropertiesSet();
        dead.start();
        try {
            StringRedisTemplate deadTpl = new StringRedisTemplate(dead);
            deadTpl.afterPropertiesSet();
            RedisDistributedLock deadLock = new RedisDistributedLock(deadTpl, CircuitBreakerRegistry.ofDefaults(), new SimpleMeterRegistry());

            assertThat(deadLock.tryAcquire("x", Duration.ofSeconds(10))).isEmpty();
        } finally {
            dead.destroy();
        }
    }

    @Test
    void emiteMetricasDeAcquireYRenew() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisDistributedLock instrumented =
                new RedisDistributedLock(redis, CircuitBreakerRegistry.ofDefaults(), registry);
        String resource = "test:metrics";

        LockHandle h = instrumented.tryAcquire(resource, Duration.ofSeconds(60)).orElseThrow(); // acquired
        assertThat(instrumented.tryAcquire(resource, Duration.ofSeconds(60))).isEmpty();          // busy
        assertThat(h.renew(Duration.ofSeconds(60))).isTrue();                                     // renew ok
        h.close();                                                                                // release released
        assertThat(h.renew(Duration.ofSeconds(60))).isFalse();                                    // renew lost
        h.close();                                                                                // release lost (ya no es nuestro)

        assertThat(counter(registry, "redis.lock.acquire", "acquired")).isEqualTo(1.0);
        assertThat(counter(registry, "redis.lock.acquire", "busy")).isEqualTo(1.0);
        assertThat(counter(registry, "redis.lock.renew", "ok")).isEqualTo(1.0);
        assertThat(counter(registry, "redis.lock.renew", "lost")).isEqualTo(1.0);
        assertThat(counter(registry, "redis.lock.release", "released")).isEqualTo(1.0);
        assertThat(counter(registry, "redis.lock.release", "lost")).isEqualTo(1.0);
    }

    private static double counter(SimpleMeterRegistry reg, String name, String result) {
        return reg.get(name).tag("result", result).counter().count();
    }
}
