package com.parosurvivors.chat.shared.infrastructure.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por TIEMPO: vacía el outbox hacia RabbitMQ cada {@code serviya.chat.outbox.poll-interval-ms}
 * (1 s). Mientras quede un lote lleno sigue publicando en la misma pasada para no acumular retraso tras una caída
 * del broker. Se puede apagar con {@code serviya.chat.outbox.relay-enabled=false} (p. ej. en pruebas sin broker).
 */
@Component
@ConditionalOnProperty(prefix = "serviya.chat.outbox", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler {

    /** Tope de lotes por pasada: acota la duración de una ejecución tras un backlog grande. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OutboxBatchPublisher publisher;

    OutboxRelayScheduler(OutboxBatchPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${serviya.chat.outbox.poll-interval-ms:1000}")
    void relay() {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (publisher.publishPendingBatch() == 0) {
                return;
            }
        }
    }
}
