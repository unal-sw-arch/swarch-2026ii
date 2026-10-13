package com.parosurvivors.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del envío de correo (proveedor Brevo por defecto). El {@code apiKey} es un secreto:
 * llega por variable de entorno {@code EMAIL_API_KEY}, nunca versionado. Con {@code enabled=false}
 * no se crea el adaptador (degradación elegante: la entrega EMAIL se marca FAILED sin romper nada).
 */
@ConfigurationProperties(prefix = "serviya.email")
public record EmailProperties(
        boolean enabled,
        String providerUrl,
        String apiKey,
        String fromEmail,
        String fromName,
        int connectTimeoutMs,
        int readTimeoutMs) {
}
