package com.parosurvivors.chat.shared.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Dos conexiones Redis con políticas distintas (guía §5.5), por eso se definen a mano (la autoconfiguración de
 * Spring solo sabe de una y se desactiva si hay un RedisConnectionFactory propio):
 * <ul>
 *   <li><b>coord</b> (instancia A, compartida {@code redis-coord}): tickets WS y rate limiting. Usuario ACL propio,
 *       solo claves {@code chat:*}, siempre con TTL.</li>
 *   <li><b>pubsub</b> (instancia C, propia del chat): difusión entre instancias. Efímera por diseño.</li>
 * </ul>
 * Timeouts cortos: Redis es opcional para estar vivo; su caída degrada, no tumba el servicio.
 */
@Configuration
class RedisConfig {

    private static LettuceConnectionFactory factory(String host, int port, String user, String password, boolean ssl,
                                                    Duration commandTimeout, Duration connectTimeout) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
        if (user != null && !user.isBlank()) {
            server.setUsername(user);
        }
        server.setPassword(password == null || password.isBlank() ? RedisPassword.none() : RedisPassword.of(password));
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                        .autoReconnect(true)
                        .build());
        if (ssl) {
            client.useSsl();
        }
        LettuceConnectionFactory f = new LettuceConnectionFactory(server, client.build());
        f.setValidateConnection(false); // no conectar al arrancar: Redis puede tardar en estar disponible
        return f;
    }

    @Bean("coordConnectionFactory")
    LettuceConnectionFactory coordConnectionFactory(
            @Value("${spring.data.redis.host}") String host, @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.username:}") String user, @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean ssl) {
        return factory(host, port, user, password, ssl, Duration.ofMillis(300), Duration.ofMillis(500));
    }

    @Bean("pubSubConnectionFactory")
    LettuceConnectionFactory pubSubConnectionFactory(
            @Value("${serviya.chat.pubsub.host}") String host, @Value("${serviya.chat.pubsub.port}") int port,
            @Value("${serviya.chat.pubsub.password:}") String password) {
        return factory(host, port, null, password, false, Duration.ofMillis(300), Duration.ofMillis(500));
    }

    @Bean("coordRedis")
    StringRedisTemplate coordRedis(@Qualifier("coordConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean("pubSubRedis")
    StringRedisTemplate pubSubRedis(@Qualifier("pubSubConnectionFactory") LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            @Qualifier("pubSubConnectionFactory") LettuceConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
