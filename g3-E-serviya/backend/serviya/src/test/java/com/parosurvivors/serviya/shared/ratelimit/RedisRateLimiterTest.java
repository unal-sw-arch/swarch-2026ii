package com.parosurvivors.serviya.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.serviya.shared.ratelimit.RateLimitProperties.Algorithm;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitProperties.FailureMode;
import com.parosurvivors.serviya.shared.ratelimit.RateLimitProperties.Policy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
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
 * Pruebas del rate limiter distribuido contra un Redis real y efímero (Testcontainers). Se SALTA (no
 * falla) sin Docker, para no romper el baseline. Cubre los criterios de aceptación de la Fase 3: rechazo
 * al superar la capacidad, recarga por tasa, concurrencia (nunca más que la capacidad), expiración y
 * modo de fallo OPEN/CLOSED.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void setUp() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    private static Policy policy(Algorithm algo, long capacity, Duration window, double refill, FailureMode fm) {
        Policy p = new Policy();
        p.setAlgorithm(algo);
        p.setCapacity(capacity);
        p.setWindow(window);
        p.setRefillPerSecond(refill);
        p.setFailureMode(fm);
        return p;
    }

    private static RedisRateLimiter limiterWith(StringRedisTemplate tpl, Map<String, Policy> policies) {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setPolicies(policies);
        return new RedisRateLimiter(tpl, props, new SimpleMeterRegistry(), CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void slidingWindowRechazaAlSuperarCapacidad() {
        RedisRateLimiter limiter = limiterWith(redis, Map.of(
                "sw", policy(Algorithm.SLIDING_WINDOW, 5, Duration.ofMinutes(15), 0, FailureMode.CLOSED)));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("sw", "ip", "1.1.1.1").allowed())
                    .as("intento %d dentro de la capacidad", i + 1).isTrue();
        }
        RateLimitDecision sixth = limiter.tryConsume("sw", "ip", "1.1.1.1");
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfter()).isPositive();
        assertThat(sixth.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sujetosDistintosNoCompartenCuota() {
        RedisRateLimiter limiter = limiterWith(redis, Map.of(
                "sw2", policy(Algorithm.SLIDING_WINDOW, 1, Duration.ofMinutes(15), 0, FailureMode.CLOSED)));

        assertThat(limiter.tryConsume("sw2", "ip", "a").allowed()).isTrue();
        assertThat(limiter.tryConsume("sw2", "ip", "a").allowed()).isFalse(); // agotado para 'a'
        assertThat(limiter.tryConsume("sw2", "ip", "b").allowed()).isTrue();  // 'b' es otro bucket
    }

    @Test
    void tokenBucketRecargaSegunTasa() throws InterruptedException {
        // capacidad 3, 10 tokens/s -> ~100ms por token
        RedisRateLimiter limiter = limiterWith(redis, Map.of(
                "tb", policy(Algorithm.TOKEN_BUCKET, 3, null, 10.0, FailureMode.OPEN)));

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("tb", "user", "42").allowed()).isTrue();
        }
        assertThat(limiter.tryConsume("tb", "user", "42").allowed()).isFalse(); // ráfaga agotada

        Thread.sleep(250); // repone ~2 tokens
        assertThat(limiter.tryConsume("tb", "user", "42").allowed()).isTrue(); // ya hay cuota de nuevo
    }

    @Test
    void concurrenciaNuncaExcedeLaCapacidad() throws InterruptedException {
        int capacity = 10;
        int threads = 40;
        RedisRateLimiter limiter = limiterWith(redis, Map.of(
                "swc", policy(Algorithm.SLIDING_WINDOW, capacity, Duration.ofMinutes(15), 0, FailureMode.CLOSED)));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (limiter.tryConsume("swc", "ip", "9.9.9.9").allowed()) {
                    allowed.incrementAndGet();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(capacity); // ni uno más que la capacidad
    }

    @Test
    void laClaveExpiraSolaTrasLaVentana() throws InterruptedException {
        RedisRateLimiter limiter = limiterWith(redis, Map.of(
                "swttl", policy(Algorithm.SLIDING_WINDOW, 2, Duration.ofMillis(300), 0, FailureMode.CLOSED)));

        limiter.tryConsume("swttl", "ip", "ttl");
        String key = RedisRateLimiter.key("ip", "ttl", "swttl");
        assertThat(redis.hasKey(key)).isTrue();

        Thread.sleep(500); // pasada la ventana + PEXPIRE
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void politicaDesconocidaPermite() {
        RedisRateLimiter limiter = limiterWith(redis, Map.of());
        assertThat(limiter.tryConsume("no-existe", "ip", "x").allowed()).isTrue();
    }

    @Test
    void modoDeFalloSegunPoliticaSiRedisNoResponde() {
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
            RedisRateLimiter limiter = limiterWith(deadTpl, Map.of(
                    "open", policy(Algorithm.SLIDING_WINDOW, 5, Duration.ofMinutes(1), 0, FailureMode.OPEN),
                    "closed", policy(Algorithm.SLIDING_WINDOW, 5, Duration.ofMinutes(1), 0, FailureMode.CLOSED)));

            assertThat(limiter.tryConsume("open", "ip", "x").allowed()).isTrue();    // OPEN deja pasar
            assertThat(limiter.tryConsume("closed", "ip", "x").allowed()).isFalse(); // CLOSED rechaza
        } finally {
            dead.destroy();
        }
    }
}
