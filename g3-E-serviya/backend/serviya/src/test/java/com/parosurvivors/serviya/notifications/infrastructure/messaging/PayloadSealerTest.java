package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Contrato criptográfico del campo {@code sealed} compartido con notifications-service. {@link #open} replica
 * el algoritmo del consumidor y el VECTOR es el mismo que abre su {@code PayloadSealerTest}: si cualquiera de
 * los dos lados cambia algoritmo, formato, derivación de clave o AAD, los tests lo detectan.
 */
public class PayloadSealerTest {

    public static final String KEY = "serviya-test-payload-key-0123456789abcdef-strong";
    static final String PLAINTEXT =
            "{\"recipientEmail\":\"ana@serviya.com\",\"protectedData\":{\"actionUrl\":\"https://serviya.test/reset?token=abc\"}}";
    static final String VECTOR = "AQIDBAUGBwgJCgsMTARrUZJs8Dq+SevhgZThNjFFNGnPSPP0gXlQuwcp392GFHmc5lxDE4GvawtjtL/HPmXzuMFzh7N+"
            + "z3rJIw69tEtH4VO1uLO+c2xxhFFKQn+BfcCnTM8NpMhOv+S6TQur8f4RaqHmYdn9ZUwzmH/7UJGzWSnzsJ2+Ag==";

    /** Apertura tal como la hace notifications-service (AES-256-GCM, IV de 12 bytes, AAD del contrato v1). */
    public static String open(String passphrase, String sealed) throws Exception {
        byte[] raw = Base64.getDecoder().decode(sealed);
        SecretKeySpec key = new SecretKeySpec(
                MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8)), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(raw, 0, 12)));
        cipher.updateAAD("serviya.notification.requested.v1".getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Arrays.copyOfRange(raw, 12, raw.length)), StandardCharsets.UTF_8);
    }

    @Test
    void consumerAlgorithm_opensTheSharedVector() throws Exception {
        assertThat(open(KEY, VECTOR)).isEqualTo(PLAINTEXT);
    }

    @Test
    void seal_roundTripsThroughTheConsumerAlgorithm_withAFreshIvEachTime() throws Exception {
        PayloadSealer sealer = new PayloadSealer(KEY);

        String first = sealer.seal(PLAINTEXT);
        String second = sealer.seal(PLAINTEXT);

        assertThat(open(KEY, first)).isEqualTo(PLAINTEXT);
        assertThat(first).isNotEqualTo(second); // IV aleatorio: mismo texto, distinto sellado
        assertThat(first).doesNotContain("ana@serviya.com");
    }

    @Test
    void refusesToStartWithWeakOrMissingKey() {
        assertThatThrownBy(() -> new PayloadSealer("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PayloadSealer("too-short")).isInstanceOf(IllegalStateException.class);
    }
}
