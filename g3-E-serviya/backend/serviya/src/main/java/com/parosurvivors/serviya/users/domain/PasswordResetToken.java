package com.parosurvivors.serviya.users.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Token de recuperación de contraseña. Mapea la tabla {@code password_reset_tokens}.
 * Sólo se persiste el hash SHA-256 del token; el valor en claro nunca se guarda
 * (ver CLAUDE.md, "Security/PII").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {
    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /** Sólo es utilizable si no ha sido usado ni ha expirado. */
    public boolean isValid() {
        return !isUsed() && !isExpired();
    }

    public void markAsUsed() {
        this.usedAt = LocalDateTime.now();
    }
}
