package com.parosurvivors.notifications.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Abre el campo {@code sealed} del mensaje {@code notification.requested}: AES-256-GCM con la clave
 * compartida {@code NOTIFICATIONS_PAYLOAD_KEY} (SHA-256 de la passphrase), formato
 * {@code base64(IV(12) || ciphertext+tag)} y AAD fija ligada a la versión del contrato.
 *
 * <p><b>Por qué existe</b>: el correo del destinatario y {@code protectedData} (p. ej. el enlace de reset
 * con su token) quedan en reposo en la tabla outbox del monolito y en la cola de RabbitMQ. Sellarlos
 * respeta la regla original del módulo ("el token nunca toca una tabla en claro"). La implementación
 * gemela que SELLA vive en el monolito ({@code notifications.infrastructure.messaging.PayloadSealer});
 * ambas deben mantenerse idénticas (lo fija un vector de prueba compartido en los tests).</p>
 */
@Component
public class PayloadSealer {

    /** Liga el cifrado a este contrato: un texto sellado para otro propósito no se acepta aquí. */
    static final byte[] AAD = "serviya.notification.requested.v1".getBytes(StandardCharsets.UTF_8);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;

    public PayloadSealer(@Value("${serviya.messaging.payload-key:}") String passphrase) {
        this.key = new SecretKeySpec(requireStrong(passphrase), "AES");
    }

    /** Devuelve el JSON en claro. Lanza {@link IllegalArgumentException} si no se puede abrir (clave/AAD/manipulación). */
    public String open(String sealed) {
        try {
            byte[] raw = Base64.getDecoder().decode(sealed);
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(raw, IV_LENGTH, raw.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Sealed payload could not be opened", ex);
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
