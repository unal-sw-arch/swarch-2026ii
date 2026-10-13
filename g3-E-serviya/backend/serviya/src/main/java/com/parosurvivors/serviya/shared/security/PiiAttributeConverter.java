package com.parosurvivors.serviya.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AttributeConverter de cifrado AES-256-GCM para campos PII (document_number, phone_number).
 * Convierte {@code String} (claro, en el dominio) &lt;-&gt; {@code byte[]} (cifrado, en BD,
 * columna VARBINARY). Cumple RNF-005.
 *
 * <p>Formato persistido: {@code IV(12 bytes) || ciphertext+tag}. La clave de 256 bits se deriva
 * por SHA-256 de la passphrase {@code ENCRYPTION_KEY} (cargada desde .env.example por DotEnvConfig o
 * desde la variable de entorno en Docker); se resuelve de forma perezosa porque Hibernate instancia el
 * converter, no Spring. No hay passphrase por defecto: si la clave falta o viene vacia, se lanza
 * una excepcion (fail-fast) para no cifrar PII con una clave conocida por el publico.
 */
@Converter
public class PiiAttributeConverter implements AttributeConverter<String, byte[]> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private volatile SecretKeySpec cachedKey;

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        SecretKeySpec secretKey = key();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
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
        SecretKeySpec secretKey = key();
        try {
            byte[] iv = Arrays.copyOfRange(dbData, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(dbData, IV_LENGTH, dbData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Fallback: the stored value is plain text (e.g. seed/legacy data not yet encrypted).
            // Return as UTF-8 string so the application can still function; the value will be
            // re-encrypted the next time this record is saved.
            return new String(dbData, StandardCharsets.UTF_8);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec local = cachedKey;
        if (local == null) {
            synchronized (this) {
                local = cachedKey;
                if (local == null) {
                    // DotEnvConfig publica las claves del .env.example como system properties (run local),
                    // pero en Docker no hay .env.example en la imagen: ENCRYPTION_KEY llega como variable de
                    // entorno (env_file/environment del compose). Por eso se consulta ambas fuentes.
                    String passphrase = System.getProperty("ENCRYPTION_KEY");
                    if (passphrase == null) {
                        passphrase = System.getenv("ENCRYPTION_KEY");
                    }
                    if (passphrase == null || passphrase.isBlank()) {
                        throw new IllegalStateException(
                                "ENCRYPTION_KEY no esta definida. El arranque se aborta para no cifrar "
                                        + "PII con una clave por defecto/insegura. Configurala en .env "
                                        + "(o en el entorno).");
                    }
                    // Rechazar valores débiles/conocidos que podrían venir de .env.example copiado sin modificar
                    if (isWeakEncryptionKey(passphrase)) {
                        throw new IllegalStateException(
                                "ENCRYPTION_KEY usa un valor inseguro (por defecto/de ejemplo). "
                                        + "Genera una passphrase segura y configurala en .env o variables de entorno.");
                    }
                    try {
                        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                                .digest(passphrase.getBytes(StandardCharsets.UTF_8));
                        local = new SecretKeySpec(keyBytes, "AES");
                        cachedKey = local;
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to derive PII encryption key", ex);
                    }
                }
            }
        }
        return local;
    }

    private static boolean isWeakEncryptionKey(String passphrase) {
        String lower = passphrase.toLowerCase();
        return lower.contains("dev-")
                || lower.contains("change-me")
                || lower.contains("example")
                || lower.contains("default")
                || passphrase.length() < 32;
    }
}
