package com.parosurvivors.serviya.profiles.infrastructure.repositories;

import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererAvailabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OffererAvailabilityRepository extends JpaRepository<OffererAvailabilityEntity, Long> {
    List<OffererAvailabilityEntity> findByOffererId(Long offererId);

    /**
     * Borrado masivo previo al reemplazo del horario ({@code setSchedule}).
     *
     * <p>Va sin {@code flushAutomatically}/{@code clearAutomatically} a proposito: un DELETE en JPQL
     * se salta el contexto de persistencia, pero hoy el unico llamador no carga ninguna entidad de
     * esta tabla antes (mapea y valida en memoria) y reinserta franjas con id nulo, asi que no hay
     * nada pendiente que bajar a la BD ni nada obsoleto que limpiar.</p>
     *
     * <p>INVARIANTE del que depende: no leer entidades de esta tabla en la misma transaccion antes
     * de llamar aqui. Si algun flujo futuro necesita hacerlo (p. ej. leer el horario actual para
     * compararlo), hay que anadir las DOS banderas — ver el ejemplo comentado en
     * {@code PasswordResetTokenRepository.markAllAsUsedByUserId}.</p>
     */
    @Modifying
    @Query("DELETE FROM OffererAvailabilityEntity e WHERE e.offererId = :offererId")
    void deleteByOffererId(@Param("offererId") Long offererId);
}
