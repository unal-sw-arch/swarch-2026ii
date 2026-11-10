package com.parosurvivors.serviya.shared.events.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vacía el outbox de eventos de integracion cada {@code serviya.events.outbox.poll-interval-ms}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "serviya.events.outbox", name = "relay-enabled",
        havingValue = "true", matchIfMissing = true)
public class DomainEventOutboxRelayScheduler {

    private static final int MAX_BATCHES_PER_RUN = 20;

    private final DomainEventOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${serviya.events.outbox.poll-interval-ms:1000}")
    public void relay() {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (publisher.publishPendingBatch() == 0) {
                return;
            }
        }
    }
}
