package com.parosurvivors.notifications.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AttributeConverter AES-256-GCM para PII en reposo (el correo del destinatario). Formato persistido:
 * {@code IV(12 bytes) || ciphertext+tag} en columna VARBINARY. Clave = SHA-256 de {@code ENCRYPTION_KEY}
 * (la del SERVICIO, distinta de la del monolito).
 *
 * <p>Es un bean de Spring: Hibernate obtiene los converters del contenedor de Spring
 * ({@code SpringBeanContainer}), así que la clave se inyecta y se valida al arrancar (fail-fast).</p>
 *
 * <p>Si una fila no se puede descifrar (clave rotada sin re-cifrar) devuelve {@code null} en vez de lanzar:
 * la bandeja sigue funcionando y solo el envío por EMAIL de esa notificación falla (queda FAILED).</p>
 */
@Component
@Converter
public class PiiAttributeConverter implements AttributeConverter<String, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(PiiAttributeConverter.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public PiiAttributeConverter(@Value("${serviya.security.encryption-key:}") String passphrase) {
        this.key = SecretKeys.aesKey("ENCRYPTION_KEY", passphrase);
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt PII attribute", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] iv = Arrays.copyOfRange(dbData, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(dbData, IV_LENGTH, dbData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Could not decrypt a PII column (wrong ENCRYPTION_KEY or corrupted value); treating as null");
            return null;
        }
    }
}
