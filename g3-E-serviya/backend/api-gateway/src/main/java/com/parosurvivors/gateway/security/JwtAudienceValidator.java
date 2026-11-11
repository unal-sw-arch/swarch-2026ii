package com.parosurvivors.gateway.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida que el access token vaya dirigido a esta API (claim {@code aud} del contrato 4.1). Spring valida
 * {@code iss} y los timestamps de serie, pero no la audiencia; sin esta comprobación, un token legítimo
 * emitido para otra audiencia del mismo emisor sería aceptado. Emitido como {@code OAuth2Error} con código
 * {@code invalid_token} para que salga por el flujo estándar de resource server (401).
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public JwtAudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "El token no va dirigido a la audiencia esperada (" + expectedAudience + ")",
                "https://tools.ietf.org/html/rfc6750#section-3.1"));
    }
}
