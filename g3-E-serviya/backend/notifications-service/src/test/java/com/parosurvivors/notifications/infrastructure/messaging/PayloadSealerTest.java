package com.parosurvivors.notifications.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Contrato criptográfico del campo {@code sealed} compartido con el monolito. El VECTOR fijo de abajo es el
 * mismo que usa {@code PayloadSealerTest} del monolito: si alguno de los dos lados cambia el algoritmo, el
 * formato, la derivación de clave o la AAD, este test falla antes de que lo haga producción.
 */
public class PayloadSealerTest {

    public static final String KEY = "serviya-test-payload-key-0123456789abcdef-strong";
    static final String PLAINTEXT =
            "{\"recipientEmail\":\"ana@serviya.com\",\"protectedData\":{\"actionUrl\":\"https://serviya.test/reset?token=abc\"}}";
    public static final String VECTOR = "AQIDBAUGBwgJCgsMTARrUZJs8Dq+SevhgZThNjFFNGnPSPP0gXlQuwcp392GFHmc5lxDE4GvawtjtL/HPmXzuMFzh7N+"
            + "z3rJIw69tEtH4VO1uLO+c2xxhFFKQn+BfcCnTM8NpMhOv+S6TQur8f4RaqHmYdn9ZUwzmH/7UJGzWSnzsJ2+Ag==";

    @Test
    void opensTheSharedVector() {
        assertThat(new PayloadSealer(KEY).open(VECTOR)).isEqualTo(PLAINTEXT);
    }

    @Test
    void rejectsWrongKey() {
        PayloadSealer other = new PayloadSealer("another-strong-payload-key-0123456789abcdef");
        assertThatThrownBy(() -> other.open(VECTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedCiphertext() {
        char[] chars = VECTOR.toCharArray();
        chars[30] = chars[30] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> new PayloadSealer(KEY).open(new String(chars)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToStartWithWeakKey() {
        assertThatThrownBy(() -> new PayloadSealer("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PayloadSealer("")).isInstanceOf(IllegalStateException.class);
    }
}
