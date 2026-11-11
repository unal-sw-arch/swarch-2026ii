package com.parosurvivors.chat.shared.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verificación del access token: firma ES256 con la clave pública del JWKS (cacheada por kid por Nimbus) +
 * {@code exp}, {@code iss}, {@code aud} y {@code typ=at+jwt}. NO se chequea {@code min_iat} (revocación):
 * lo hace el gateway. El chat solo revalida como defensa en profundidad.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtDecoderConfig {

    static final String ACCESS_TOKEN_TYPE = "at+jwt";

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                          JwtProperties props) {
        if (props.issuer() == null || props.issuer().isBlank()) {
            throw new IllegalStateException("JWT_ISSUER es obligatorio: el chat debe validar el claim iss");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                // Por defecto Nimbus solo acepta typ=JWT (o ausente) y rechazaría at+jwt.
                .jwtProcessorCustomizer(p -> p.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(ACCESS_TOKEN_TYPE))))
                .build();
        decoder.setJwtValidator(tokenValidator(props));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties props) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                audienceValidator(props.audience()));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "El claim aud no incluye " + audience, null));
        };
    }
}
