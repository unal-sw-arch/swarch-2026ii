package com.parosurvivors.notifications.application.ports.output;

import com.parosurvivors.notifications.domain.DeliveryStatus;
import com.parosurvivors.notifications.domain.NotificationDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryPersistencePort {
    NotificationDelivery save(NotificationDelivery delivery);
    NotificationDelivery update(NotificationDelivery delivery);
    Optional<NotificationDelivery> findById(Long id);
    Optional<NotificationDelivery> findByIdAndUserId(Long id, Long userId);
    Page<NotificationDelivery> findDeliveriesByUserId(Long userId, Boolean read, Long channelId, DeliveryStatus status, Pageable pageable);

    /**
     * Reclama hasta {@code limit} entregas en el estado dado con menos de {@code maxAttempts} intentos para
     * reintentarlas. Las filas quedan BLOQUEADAS hasta el fin de la transacción y las ya bloqueadas por otra
     * instancia se saltan ({@code FOR UPDATE SKIP LOCKED}): con ≥2 réplicas cada entrega la reintenta una sola.
     * Requiere transacción activa.
     */
    List<NotificationDelivery> claimForRetry(DeliveryStatus status, int maxAttempts, int limit);
}
