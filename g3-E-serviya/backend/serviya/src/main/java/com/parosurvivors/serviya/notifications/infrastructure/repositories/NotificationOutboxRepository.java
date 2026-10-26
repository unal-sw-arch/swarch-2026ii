package com.parosurvivors.serviya.notifications.infrastructure.repositories;

import com.parosurvivors.serviya.notifications.infrastructure.entities.NotificationOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, Long> {

    /**
     * Reclama el siguiente lote en orden de llegada. {@code FOR UPDATE SKIP LOCKED}: con ≥2 instancias del
     * monolito cada una publica filas distintas (sin doble publicación por relay concurrente y sin esperar).
     */
    @Query(value = "SELECT * FROM notification_outbox ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<NotificationOutboxEntity> claimBatch(@Param("limit") int limit);
}
