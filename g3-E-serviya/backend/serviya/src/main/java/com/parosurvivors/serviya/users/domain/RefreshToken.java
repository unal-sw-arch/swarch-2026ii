package com.parosurvivors.serviya.users.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh token opaco (Fase 2 de la centralización de auth). Credencial de larga vida que permite emitir
 * access tokens nuevos sin volver a introducir contraseña. El valor en claro NUNCA se persiste: se guarda
 * su hash SHA-256 ({@code tokenHash}), igual que el token de recuperación de contraseña.
 *
 * <p>Pertenece a una <b>familia</b> ({@code familyId}): login/registro abren una familia nueva y cada
 * refresh la <b>rota</b> (el token usado queda {@code replaced} y se emite uno nuevo en la misma familia).
 * La familia tiene un tope de vida <b>absoluto</b> ({@code expiresAt}) que no se renueva al rotar. Reusar
 * un token ya rotado o revocado = robo → se revoca la familia entera.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {

    private Long id;
    private Long userId;
    /** Identifica la cadena de rotaciones de una sesión; se conserva al rotar. */
    private String familyId;
    /** SHA-256 en hex del valor en claro; el valor en claro nunca se persiste. */
    private String tokenHash;
    /** Momento de emisión de ESTE token (se usa para la ventana de gracia de reuso). */
    private LocalDateTime issuedAt;
    /** Vencimiento absoluto de la familia; NO se extiende al rotar. */
    private LocalDateTime expiresAt;
    /** Revocado (logout, logout-all, detección de reuso, cuenta baneada/eliminada). */
    private LocalDateTime revokedAt;
    /** Id del token que lo reemplazó al rotar; distinto de null ⇒ este token ya se usó. */
    private Long replacedById;
    private String userAgent;
    private String ip;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Ya fue rotado (usado): si vuelve a presentarse, es reuso. */
    public boolean isRotated() {
        return replacedById != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** Vivo y sin usar: ni revocado, ni rotado, ni expirado. */
    public boolean isActive(LocalDateTime now) {
        return !isRevoked() && !isRotated() && !isExpired(now);
    }

    /** ¿Se emitió hace menos de {@code grace}? (varias pestañas refrescando casi a la vez). */
    public boolean withinGrace(LocalDateTime now, Duration grace) {
        return issuedAt != null && Duration.between(issuedAt, now).compareTo(grace) <= 0;
    }

    public void markReplacedBy(Long newTokenId) {
        this.replacedById = newTokenId;
    }

    public void revoke(LocalDateTime when) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
        }
    }
}
