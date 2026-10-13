package com.parosurvivors.serviya.shared.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Conexión a la instancia {@code redis-coord} (locks; rate limit en Fase 3). Adopción <b>Vía B</b>:
 * solo se crea la fábrica de coord (marcada {@link Primary}); la de {@code cache} llega en la Fase 6.
 *
 * <p>Al definir una {@link LettuceConnectionFactory} propia, la autoconfiguración de Spring Boot
 * ({@code spring.data.redis.*}) se desactiva sola, por eso se usa el contrato propio {@code app.redis.*}
 * ({@link AppRedisProperties}). El health indicator autoconfigurado se <b>excluye</b> (usa {@code INFO},
 * que el usuario de servicio no tiene por mínimo privilegio); en su lugar se registra
 * {@link #redisHealthIndicator} basado solo en {@code PING}. Ver {@code spring.autoconfigure.exclude}.</p>
 *
 * <p>Solo implementa {@code mode=standalone}; cualquier otro valor <b>falla al arrancar</b> con un
 * mensaje claro (sentinel/cluster se implementan en la Fase 8 sin cambiar el contrato).</p>
 */
@Configuration
@EnableConfigurationProperties(AppRedisProperties.class)
public class RedisCoordConfig {

    @Bean
    @Primary
    public LettuceConnectionFactory coordRedisConnectionFactory(
            AppRedisProperties props,
            @Value("${spring.application.name:serviya}") String appName) {
        return buildStandaloneFactory(props.getCoord(), appName + "-coord");
    }

    @Bean
    public StringRedisTemplate coordRedisTemplate(
            @Qualifier("coordRedisConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Fábrica del <b>hot-path</b> (rate limiting): misma instancia {@code redis-coord} pero con un
     * command timeout mucho más corto ({@code app.redis.coord.hot-command-timeout}, por defecto 300ms),
     * porque está en el camino de cada petición. El lock de los schedulers (background) sigue usando la
     * fábrica normal con 2s. Timeouts diferenciados (Fase 4, decisión 2).
     */
    @Bean
    public LettuceConnectionFactory coordHotRedisConnectionFactory(
            AppRedisProperties props,
            @Value("${spring.application.name:serviya}") String appName) {
        return buildStandaloneFactory(
                props.getCoord(), appName + "-coord-hot", props.getCoord().getHotCommandTimeout());
    }

    @Bean
    public StringRedisTemplate coordHotRedisTemplate(
            @Qualifier("coordHotRedisConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Health indicator de {@code redis-coord} por {@code PING} (no {@code INFO}): el usuario de servicio
     * {@code svc-serviya-coord} solo tiene {@code +ping} por mínimo privilegio, así que el indicator
     * autoconfigurado de Spring Boot (que ejecuta {@code INFO}) se excluye y se usa este. El nombre del
     * bean {@code redisHealthIndicator} lo registra bajo el id de componente {@code redis} (grupo de health
     * homónimo). Los detalles solo se muestran con {@code show-details: when_authorized}.
     */
    @Bean("redisHealthIndicator")
    public HealthIndicator redisHealthIndicator(
            @Qualifier("coordRedisConnectionFactory") LettuceConnectionFactory factory) {
        return () -> {
            try (RedisConnection conn = factory.getConnection()) {
                String pong = conn.ping();
                return "PONG".equalsIgnoreCase(pong)
                        ? Health.up().withDetail("ping", pong).build()
                        : Health.down().withDetail("ping", String.valueOf(pong)).build();
            } catch (Exception ex) {
                return Health.down(ex).build();
            }
        };
    }

    /**
     * Construye una {@link LettuceConnectionFactory} standalone desde un nodo del contrato. Reutilizable
     * por la configuración de caché (Fase 6). Falla fuerte si el modo no es {@code standalone}.
     */
    static LettuceConnectionFactory buildStandaloneFactory(AppRedisProperties.Node node, String clientName) {
        return buildStandaloneFactory(node, clientName, node.getTimeout());
    }

    /** Variante con command timeout explícito (hot-path vs. background); el connect timeout no cambia. */
    static LettuceConnectionFactory buildStandaloneFactory(
            AppRedisProperties.Node node, String clientName, java.time.Duration commandTimeout) {
        if (!"standalone".equalsIgnoreCase(node.getMode())) {
            throw new IllegalStateException(
                    "app.redis.*.mode='" + node.getMode() + "' no implementado aún (solo 'standalone'). "
                            + "Sentinel/Cluster llegan en la Fase 8.");
        }
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(node.getHost(), node.getPort());
        if (node.getUsername() != null && !node.getUsername().isBlank()) {
            standalone.setUsername(node.getUsername());
        }
        if (node.getPassword() != null && !node.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(node.getPassword()));
        }

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(node.getConnectTimeout()).build())
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig =
                LettuceClientConfiguration.builder()
                        .commandTimeout(commandTimeout)
                        .clientName(clientName)
                        .clientOptions(clientOptions);
        if (node.isSsl()) {
            clientConfig.useSsl();
        }

        return new LettuceConnectionFactory(standalone, clientConfig.build());
    }
}
