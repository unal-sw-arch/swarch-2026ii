package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Puerto de salida de persistencia de refresh tokens (Fase 2). Habla tipos de dominio; el adaptador JPA
 * vive en infraestructura. El lookup es por {@code tokenHash} (la columna es única).
 */
public interface RefreshTokenPort {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marca un token como rotado (apunta al que lo reemplaza). Update masivo por id: NO pasa por la
     * entidad gestionada, así evita el fallo de {@code @Version null} al actualizar desde el dominio
     * (ESTADO §8). Devuelve 1 si lo rotó, 0 si ya estaba rotado (carrera).
     */
    int markReplaced(Long oldTokenId, Long newTokenId);

    /** Revoca de una sola pasada todos los tokens vivos de una familia (logout, reuso detectado). */
    int revokeFamily(String familyId, LocalDateTime revokedAt);

    /** Revoca de una sola pasada todos los tokens vivos de un usuario (logout-all). */
    int revokeAllForUser(Long userId, LocalDateTime revokedAt);

    /** Limpieza: borra tokens ya expirados o revocados antes del corte. */
    int deleteExpiredOrRevokedBefore(LocalDateTime cutoff);
}
