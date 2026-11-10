package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.infrastructure.entities.RefreshTokenEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /** Lookup de la rotación. La columna tiene UNIQUE, así que devuelve 0 o 1 fila. */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Marca un token como rotado (update por id). {@code AND replaced_by_id IS NULL} hace la rotación
     * idempotente ante carreras (dos pestañas rotando el mismo token: solo una gana). Update masivo:
     * no pasa por la entidad gestionada, evitando el fallo de @Version null (ESTADO §8).
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.replacedById = :newId "
            + "WHERE t.id = :oldId AND t.replacedById IS NULL")
    int markReplaced(@Param("oldId") Long oldId, @Param("newId") Long newId);

    /**
     * Revoca de una pasada los tokens vivos de una familia (logout, reuso detectado). Sin banderas
     * flush/clear: el llamador (servicio de refresh) no deja contexto sucio sobre estas filas
     * (ver convención @Modifying del proyecto).
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :revokedAt "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") LocalDateTime revokedAt);

    /** Revoca de una pasada todos los tokens vivos de un usuario (logout-all). */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revokedAt = :revokedAt "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);

    /** Limpieza periódica: fuera ya no sirven ni para auditoría (expirados o revocados). */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff OR t.revokedAt < :cutoff")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
