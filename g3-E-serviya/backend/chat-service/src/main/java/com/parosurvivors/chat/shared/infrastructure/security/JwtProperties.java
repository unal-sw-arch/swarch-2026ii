package com.parosurvivors.chat.shared.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Claims esperados en el access token ES256 del emisor (DP-03). */
@ConfigurationProperties(prefix = "serviya.security.jwt")
public record JwtProperties(String issuer, String audience) {
}
