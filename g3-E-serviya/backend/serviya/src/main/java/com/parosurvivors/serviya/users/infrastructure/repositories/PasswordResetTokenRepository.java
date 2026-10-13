package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.infrastructure.entities.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    List<PasswordResetTokenEntity> findByUserId(Long userId);

    /** Lookup del flujo de recuperacion. La columna tiene UNIQUE, asi que devuelve 0 o 1 fila. */
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Quema de una sola pasada los tokens del usuario que sigan vivos (used_at IS NULL).
     *
     * <p>Las dos banderas van EN PAREJA y son necesarias aqui porque consumeToken llama a este
     * bulk update con trabajo pendiente en el contexto de persistencia: antes ha cargado el token
     * por su hash (queda gestionado) y le ha hecho merge para marcarlo usado. Un update masivo en
     * JPQL va directo a la BD, saltandose ese contexto.</p>
     *
     * <ul>
     *   <li>{@code flushAutomatically}: baja el merge pendiente ANTES del update, para que el
     *       {@code WHERE used_at IS NULL} ya no vea el token recien consumido. Sin esto dependemos
     *       del auto-flush de Hibernate, que funciona pero es una heuristica implicita.</li>
     *   <li>{@code clearAutomatically}: vacia el contexto DESPUES, para que nadie lea en la misma
     *       transaccion una entidad cuyo used_at cambio a su espalda.</li>
     * </ul>
     *
     * <p>OJO: {@code clearAutomatically} en solitario es peligroso — {@code clear()} DESCARTA los
     * cambios que aun no se hayan bajado a la BD. Si se pone una, se ponen las dos.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :usedAt "
            + "WHERE t.userId = :userId AND t.usedAt IS NULL")
    int markAllAsUsedByUserId(@Param("userId") Long userId, @Param("usedAt") LocalDateTime usedAt);

    /**
     * Limpieza periodica: los tokens expirados ya no sirven ni para auditoria.
     *
     * <p>Sin banderas a proposito: lo invoca el scheduler en una transaccion propia que no carga
     * ninguna entidad antes ni lee nada despues, asi que no hay contexto que sincronizar. Si algun
     * dia se llama desde un flujo mas grande, revisar esta decision.</p>
     */
    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
