package com.parosurvivors.serviya.notifications.infrastructure.adapters.input;

import com.parosurvivors.serviya.notifications.infrastructure.messaging.NotificationOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: vacía el outbox de notificaciones hacia RabbitMQ cada
 * {@code serviya.notifications.outbox.poll-interval-ms} (1 s por defecto). Mientras quede un lote lleno,
 * sigue publicando en la misma pasada para no acumular retraso tras una caída del broker.
 *
 * <p>Sin lock distribuido: el lote se reclama con {@code FOR UPDATE SKIP LOCKED}, así varias instancias
 * del monolito pueden ejecutar el relay a la vez sin publicar dos veces la misma fila.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "serviya.notifications.outbox", name = "relay-enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationOutboxRelayScheduler {

    /** Tope de lotes por pasada: acota la duración de una ejecución tras un backlog grande. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final NotificationOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${serviya.notifications.outbox.poll-interval-ms:1000}")
    public void relay() {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (publisher.publishPendingBatch() == 0) {
                return;
            }
        }
    }
}
