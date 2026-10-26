package com.parosurvivors.serviya.notifications.application.ports.output;

import com.parosurvivors.serviya.notifications.application.dto.NotificationRequest;

/**
 * Puerto de salida: encola la petición en el outbox transaccional. Debe ejecutarse en la MISMA transacción
 * que la operación de negocio que la origina (patrón Transactional Outbox, M3).
 */
public interface NotificationOutboxPort {

    void enqueue(NotificationRequest request);
}
