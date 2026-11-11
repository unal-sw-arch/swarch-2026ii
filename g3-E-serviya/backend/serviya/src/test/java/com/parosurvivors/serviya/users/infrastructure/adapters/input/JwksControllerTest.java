package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.serviya.users.infrastructure.security.JwtKeys;
import com.parosurvivors.serviya.users.infrastructure.security.JwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * El JWKS expone SOLO la clave pública ES256 con los parámetros esperados y NUNCA el parámetro privado
 * {@code d} (criterio de aceptación de la Fase 1, T1.5).
 */
class JwksControllerTest {

    private final JwksController controller = new JwksController(new JwtKeys(JwtTestSupport.properties()));

    @Test
    @SuppressWarnings("unchecked")
    void publishesPublicJwkWithoutPrivateParameter() {
        ResponseEntity<Map<String, Object>> response = controller.jwks();

        List<Map<String, Object>> jwks = (List<Map<String, Object>>) response.getBody().get("keys");
        assertThat(jwks).hasSize(1);
        Map<String, Object> jwk = jwks.get(0);

        assertThat(jwk.get("kty")).isEqualTo("EC");
        assertThat(jwk.get("crv")).isEqualTo("P-256");
        assertThat(jwk.get("kid")).isEqualTo(JwtTestSupport.KEY_ID);
        assertThat(jwk.get("alg")).isEqualTo("ES256");
        assertThat(jwk.get("use")).isEqualTo("sig");
        assertThat(jwk).containsKeys("x", "y");
        assertThat(jwk).doesNotContainKey("d"); // el parámetro privado NUNCA se publica
    }

    @Test
    void setsCacheControlForVerifiersToCacheTheKeys() {
        ResponseEntity<Map<String, Object>> response = controller.jwks();

        assertThat(response.getHeaders().getCacheControl()).contains("max-age=300");
    }
}
