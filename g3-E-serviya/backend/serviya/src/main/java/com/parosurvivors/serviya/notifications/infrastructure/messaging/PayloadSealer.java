package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sella el campo {@code sealed} del mensaje {@code notification.requested}: AES-256-GCM con la clave
 * compartida {@code NOTIFICATIONS_PAYLOAD_KEY} (SHA-256 de la passphrase), formato
 * {@code base64(IV(12) || ciphertext+tag)} y AAD fija ligada a la versión del contrato.
 *
 * <p>Por qué: el correo del destinatario y {@code protectedData} (p. ej. el enlace de reset con su token)
 * quedan en reposo en la tabla outbox y en la cola de RabbitMQ. Sellarlos mantiene la regla original del
 * módulo: el token nunca toca una tabla en claro. La implementación gemela que ABRE vive en
 * notifications-service; ambas deben mantenerse idénticas (lo fija un vector de prueba compartido).</p>
 */
@Component
public class PayloadSealer {

    static final byte[] AAD = "serviya.notification.requested.v1".getBytes(StandardCharsets.UTF_8);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public PayloadSealer(@Value("${serviya.notifications.payload-key:}") String passphrase) {
        this.key = new SecretKeySpec(requireStrong(passphrase), "AES");
    }

    public String seal(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to seal notification payload", ex);
        }
    }

    private static byte[] requireStrong(String secret) {
        if (secret == null || secret.isBlank() || secret.length() < 32
                || secret.toLowerCase().contains("change-me") || secret.toLowerCase().contains("example")) {
            throw new IllegalStateException("NOTIFICATIONS_PAYLOAD_KEY no esta definida o es debil (< 32 caracteres "
                    + "o valor de ejemplo). Debe ser la MISMA en el monolito y en notifications-service.");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
