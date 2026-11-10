package com.parosurvivors.serviya.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.Test;

/**
 * Fail-fast de la carga de claves ES256 (T1.3): el arranque se aborta si falta una clave, no decodifica,
 * no es EC P-256 o falta el {@code kid}; así nunca se firma con material inválido.
 */
class JwtKeysTest {

    @Test
    void loadsValidP256KeyPair() {
        JwtKeys keys = new JwtKeys(JwtTestSupport.properties());

        assertThat(keys.privateKey()).isInstanceOf(ECPrivateKey.class);
        assertThat(keys.publicKey()).isInstanceOf(ECPublicKey.class);
        assertThat(keys.publicKey().getParams().getCurve().getField().getFieldSize()).isEqualTo(256);
        assertThat(keys.keyId()).isEqualTo(JwtTestSupport.KEY_ID);
    }

    @Test
    void failsFastWhenPrivateKeyMissing() {
        JwtProperties props = JwtTestSupport.properties();
        props.setPrivateKey("");

        assertThatThrownBy(() -> new JwtKeys(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void failsFastWhenPublicKeyMissing() {
        JwtProperties props = JwtTestSupport.properties();
        props.setPublicKey("   ");

        assertThatThrownBy(() -> new JwtKeys(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }

    @Test
    void failsFastWhenKeyIdBlank() {
        JwtProperties props = JwtTestSupport.properties();
        props.setKeyId("  ");

        assertThatThrownBy(() -> new JwtKeys(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_KEY_ID");
    }

    @Test
    void failsFastWhenPrivateKeyIsNotValidBase64() {
        JwtProperties props = JwtTestSupport.properties();
        props.setPrivateKey("###-not-base64-###");

        assertThatThrownBy(() -> new JwtKeys(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void failsFastWhenPrivateKeyIsNotAnEcKey() {
        // Un DER bien formado en base64 pero que no es una clave EC P-256 (bytes arbitrarios) debe rechazarse.
        JwtProperties props = JwtTestSupport.properties();
        props.setPrivateKey("AAECAwQFBgcICQoLDA0ODw==");

        assertThatThrownBy(() -> new JwtKeys(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }
}
