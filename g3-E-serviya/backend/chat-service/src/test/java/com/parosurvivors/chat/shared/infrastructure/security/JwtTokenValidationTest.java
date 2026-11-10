package com.parosurvivors.chat.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtTokenValidationTest {

    private static final JwtProperties PROPS = new JwtProperties("https://auth.serviya", "marketplace-api");

    private static Jwt jwt(String iss, List<String> aud, Instant exp) {
        return Jwt.withTokenValue("t").header("alg", "ES256").header("typ", "at+jwt")
                .subject("42").issuer(iss).audience(aud)
                .issuedAt(exp.minusSeconds(3600)).expiresAt(exp).build();
    }

    private static boolean valid(Jwt jwt) {
        return !JwtDecoderConfig.tokenValidator(PROPS).validate(jwt).hasErrors();
    }

    @Test
    void token_con_iss_aud_y_exp_correctos_es_valido() {
        assertThat(valid(jwt("https://auth.serviya", List.of("marketplace-api"), Instant.now().plusSeconds(600)))).isTrue();
    }

    @Test
    void emisor_incorrecto_se_rechaza() {
        assertThat(valid(jwt("https://otro", List.of("marketplace-api"), Instant.now().plusSeconds(600)))).isFalse();
    }

    @Test
    void audiencia_incorrecta_se_rechaza() {
        assertThat(valid(jwt("https://auth.serviya", List.of("otra-api"), Instant.now().plusSeconds(600)))).isFalse();
    }

    @Test
    void token_vencido_se_rechaza() {
        assertThat(valid(jwt("https://auth.serviya", List.of("marketplace-api"), Instant.now().minusSeconds(600)))).isFalse();
    }
}
