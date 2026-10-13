package com.parosurvivors.notifications.infrastructure.security;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Derivación y validación de las claves simétricas del servicio. Misma política que el monolito: sin valor
 * por defecto, fail-fast si falta o es débil, y SHA-256 de la passphrase para obtener 256 bits.
 */
final class SecretKeys {

    private SecretKeys() {
    }

    static byte[] requireStrongSecret(String name, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(name + " no esta definida. El arranque se aborta para no usar "
                    + "una clave por defecto/insegura. Configurala en el entorno (.env).");
        }
        String lower = secret.toLowerCase();
        if (lower.contains("dev-") || lower.contains("change-me") || lower.contains("example")
                || lower.contains("default") || secret.length() < 32) {
            throw new IllegalStateException(name + " usa un valor inseguro (por defecto/de ejemplo o < 32 "
                    + "caracteres). Genera un secreto aleatorio y configuralo en el entorno.");
        }
        return sha256(secret);
    }

    static SecretKeySpec aesKey(String name, String secret) {
        return new SecretKeySpec(requireStrongSecret(name, secret), "AES");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
