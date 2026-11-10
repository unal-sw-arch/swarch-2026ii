package com.parosurvivors.serviya.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * El cifrado PII (RNF-005) debe ser reversible y no exponer el dato en claro en BD.
 */
class PiiAttributeConverterTest {

    private static final String TEST_KEY = "serviya-test-pii-key-for-unit-tests-32chars";

    private final PiiAttributeConverter converter = new PiiAttributeConverter();

    @BeforeAll
    static void setUpEncryptionKey() {
        System.setProperty("ENCRYPTION_KEY", TEST_KEY);
    }

    @AfterAll
    static void clearEncryptionKey() {
        System.clearProperty("ENCRYPTION_KEY");
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String plain = "1098765432";

        byte[] encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plain);
        // No debe quedar el texto plano en los bytes persistidos.
        assertThat(new String(encrypted, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(plain);
    }

    @Test
    void usesRandomIv_soSameInputProducesDifferentCiphertext() {
        byte[] first = converter.convertToDatabaseColumn("3001234567");
        byte[] second = converter.convertToDatabaseColumn("3001234567");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void handlesNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
