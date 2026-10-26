package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.dto.result.IssuedResetToken;
import com.parosurvivors.serviya.users.application.dto.result.TokenValidationResult;
import com.parosurvivors.serviya.users.application.ports.input.PasswordResetTokenServicePort;
import com.parosurvivors.serviya.users.application.ports.output.PasswordResetTokenPersistencePort;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida de los tokens de recuperación de contraseña (RF-003).
 *
 * <p><b>Por qué SHA-256 y no BCrypt</b> (a diferencia de la contraseña): el token lo genera
 * {@link SecureRandom} con 256 bits de entropía, así que no hay nada que ralentizar frente a fuerza
 * bruta. Necesitamos, en cambio, que el hash sea determinista para poder buscar por {@code token_hash}
 * con su índice; BCrypt lleva salt propio por hash y haría imposible el lookup. La contraseña, elegida
 * por un humano y de baja entropía, sí va con BCrypt.</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordResetTokenService implements PasswordResetTokenServicePort {

    /** 32 bytes = 256 bits de entropía → ~43 caracteres en Base64 url-safe. */
    private static final int TOKEN_BYTES = 32;

    /** Thread-safe; se reutiliza para no re-sembrar en cada solicitud. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Url-safe y sin padding: el token viaja como query param del enlace del correo. */
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final PasswordResetTokenPersistencePort passwordResetTokenPersistencePort;

    /** Ventana de validez del enlace. Corta a propósito: es una credencial que viaja por correo. */
    @Value("${serviya.password-reset.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Override
    @Transactional
    public IssuedResetToken createToken(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        // Un único token utilizable a la vez: pedir uno nuevo quema los anteriores, de modo que un
        // enlace viejo (o filtrado) deja de servir en cuanto el usuario vuelve a solicitar el reset.
        passwordResetTokenPersistencePort.invalidateAllForUser(userId, now);

        String rawToken = generateRawToken();
        LocalDateTime expiresAt = now.plusMinutes(tokenTtlMinutes);

        passwordResetTokenPersistencePort.save(PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return new IssuedResetToken(rawToken, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenValidationResult validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenValidationResult.NOT_FOUND;
        }
        return passwordResetTokenPersistencePort.findByTokenHash(sha256Hex(rawToken))
                .map(token -> {
                    if (token.isUsed()) {
                        return TokenValidationResult.USED;
                    }
                    if (token.isExpired()) {
                        return TokenValidationResult.EXPIRED;
                    }
                    return TokenValidationResult.VALID;
                })
                .orElse(TokenValidationResult.NOT_FOUND);
    }

    @Override
    @Transactional
    public PasswordResetToken consumeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidStateException(GENERIC_INVALID_TOKEN_MESSAGE);
        }

        PasswordResetToken token = passwordResetTokenPersistencePort.findByTokenHash(sha256Hex(rawToken))
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new InvalidStateException(GENERIC_INVALID_TOKEN_MESSAGE));

        LocalDateTime now = LocalDateTime.now();
        token.markAsUsed();
        passwordResetTokenPersistencePort.update(token);

        // Y el resto de tokens vivos del usuario: tras un reset exitoso ninguno debe seguir sirviendo.
        passwordResetTokenPersistencePort.invalidateAllForUser(token.getUserId(), now);

        return token;
    }

    @Override
    @Transactional
    public void markTokenAsUsed(Long tokenId) {
        PasswordResetToken token = passwordResetTokenPersistencePort.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Password reset token not found: " + tokenId));
        token.markAsUsed();
        passwordResetTokenPersistencePort.update(token);
    }

    @Override
    @Transactional
    public int deleteExpiredTokens(LocalDateTime cutoff) {
        return passwordResetTokenPersistencePort.deleteExpiredBefore(cutoff);
    }

    /** Valor aleatorio criptográficamente seguro. Es la credencial: nunca se loguea ni se persiste. */
    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /** SHA-256 en hex (64 chars, encaja con CHAR(64)). Determinista para permitir el lookup indexado. */
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno está roto.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
