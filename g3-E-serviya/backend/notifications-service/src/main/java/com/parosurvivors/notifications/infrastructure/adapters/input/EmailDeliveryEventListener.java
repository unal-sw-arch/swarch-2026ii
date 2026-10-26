package com.parosurvivors.notifications.infrastructure.adapters.input;

import com.parosurvivors.notifications.application.events.EmailDeliveryRequestedEvent;
import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada (driving) que dispara el envío real de una entrega EMAIL una vez que la
 * transacción que registró la notificación confirmó (AFTER_COMMIT). Fino: solo delega en el puerto, que
 * ejecuta el envío en una transacción NUEVA (REQUIRES_NEW).
 */
@Component
@RequiredArgsConstructor
public class EmailDeliveryEventListener {

    private final NotificationDeliveryServicePort notificationDeliveryServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailDeliveryRequested(EmailDeliveryRequestedEvent event) {
        notificationDeliveryServicePort.sendPendingEmail(event.deliveryId(), event.protectedData());
    }
}
