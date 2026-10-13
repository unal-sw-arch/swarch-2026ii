package com.parosurvivors.notifications.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registra el {@link RestClient} que habla con la API del proveedor de correo (Brevo). Solo se crea
 * cuando {@code serviya.email.enabled=true}; si no, no hay adaptador de email y la entrega EMAIL degrada
 * a FAILED. Los timeouts son obligatorios: acotan la llamada externa para no colgar el envío.
 */
@Configuration
public class EmailClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "serviya.email", name = "enabled", havingValue = "true")
    public RestClient emailRestClient(EmailProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.providerUrl())
                // Brevo autentica con el header 'api-key'. Para Resend/SendGrid sería 'Authorization: Bearer …'.
                .defaultHeader("api-key", props.apiKey())
                .defaultHeader("accept", "application/json")
                .defaultHeader("content-type", "application/json")
                .requestFactory(factory)
                .build();
    }
}
