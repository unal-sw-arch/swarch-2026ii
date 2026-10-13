package com.parosurvivors.notifications.application.ports.input;

import com.parosurvivors.notifications.application.dto.result.NotificationDeliveryResult;
import com.parosurvivors.notifications.domain.NotificationDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Puerto de entrada de NotificationDeliveryService — entrega por canal y consulta de notificaciones.
 * deliver es interno; getDeliveries devuelve el Result agregado (delivery + notificacion). Nunca tipos web.
 */
public interface NotificationDeliveryServicePort {

    NotificationDelivery deliver(Long notificationId, Long channelId, Map<String, String> protectedData);

    /**
     * Envía una entrega EMAIL que quedó PENDING. Lo invoca el listener AFTER_COMMIT en una transacción
     * NUEVA, para no bloquear ni revertir el registro con la llamada al proveedor externo.
     */
    void sendPendingEmail(Long deliveryId, Map<String, String> protectedData);

    /**
     * Reintenta las entregas en estado FAILED que aún no agotaron el máximo de intentos configurado.
     * Lo dispara una tarea programada. Devuelve cuántas se reintentaron. El máximo lo resuelve la
     * implementación desde {@code serviya.notifications.max-delivery-attempts}.
     */
    int retryFailedDeliveries();

    Page<NotificationDeliveryResult> getDeliveries(Long userId, Boolean read, Long channelId,
                                                   String status, Pageable pageable);

    void markAsRead(Long deliveryId, Long userId);
}
