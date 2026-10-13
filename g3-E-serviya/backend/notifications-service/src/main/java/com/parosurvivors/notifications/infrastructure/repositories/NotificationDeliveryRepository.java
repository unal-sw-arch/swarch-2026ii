package com.parosurvivors.notifications.infrastructure.repositories;

import com.parosurvivors.notifications.infrastructure.entities.NotificationDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, Long>,
        JpaSpecificationExecutor<NotificationDeliveryEntity> {

    /**
     * Reclama entregas para el reintento programado. {@code FOR UPDATE SKIP LOCKED} (MySQL 8+): bloquea las
     * filas devueltas hasta el commit y SALTA las que ya tiene bloqueadas otra réplica, así dos instancias no
     * reintentan la misma entrega. Sustituye al lock distribuido sobre redis-coord del monolito: el servicio
     * es dueño de su BD y no necesita un sistema de lock aparte para esto.
     */
    @Query(value = "SELECT * FROM notification_deliveries "
            + "WHERE delivery_status = :status AND attempts < :maxAttempts "
            + "ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationDeliveryEntity> claimForRetry(@Param("status") String status,
                                                   @Param("maxAttempts") int maxAttempts,
                                                   @Param("limit") int limit);

    @Query("SELECT nd FROM NotificationDeliveryEntity nd " +
           "JOIN NotificationEntity n ON nd.notificationId = n.id " +
           "WHERE nd.id = :id AND n.userId = :userId")
    Optional<NotificationDeliveryEntity> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
