package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenPersistencePort {
    PasswordResetToken save(PasswordResetToken token);
    PasswordResetToken update(PasswordResetToken token);
    Optional<PasswordResetToken> findById(Long id);
    List<PasswordResetToken> findByUserId(Long userId);

    /**
     * Busca por el hash SHA-256 del token. Es la UNICA via de resolucion del flujo de recuperacion:
     * el userId nunca se toma del cliente (la URL del correo solo lleva el token).
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Quema (marca como usados) todos los tokens vivos del usuario. Devuelve cuantos afecto.
     * Se usa al emitir uno nuevo y tras un cambio de contrasena exitoso.
     */
    int invalidateAllForUser(Long userId, LocalDateTime usedAt);

    /** Borra los tokens ya expirados antes del corte. Devuelve cuantos borro. */
    int deleteExpiredBefore(LocalDateTime cutoff);
}
