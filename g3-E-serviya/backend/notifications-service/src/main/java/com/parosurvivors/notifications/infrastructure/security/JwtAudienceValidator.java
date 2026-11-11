package com.parosurvivors.notifications.infrastructure.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida el claim {@code aud} del access token (contrato 4.1) en notif como resource server (Fase 4). Misma
 * lógica que el validador del gateway/monolito; se duplica (servicio distinto) a propósito.
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
