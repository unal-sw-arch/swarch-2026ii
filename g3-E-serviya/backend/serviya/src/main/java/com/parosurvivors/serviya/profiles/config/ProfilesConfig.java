package com.parosurvivors.serviya.profiles.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del módulo profiles.
 * Define beans compartidos como WebClient para geocodificación.
 */
@Configuration
public class ProfilesConfig {

    @Bean
    public WebClient geocodingWebClient() {
        return WebClient.builder().build();
    }
}