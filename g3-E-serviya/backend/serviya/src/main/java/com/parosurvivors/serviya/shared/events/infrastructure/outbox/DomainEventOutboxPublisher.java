package com.parosurvivors.serviya.shared.events.infrastructure.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relay del outbox de eventos de integracion. Publica en el exchange/routing key de cada fila y
 * borra cuando RabbitMQ confirma. Si el broker falla, las filas quedan y se reintentan.
 */
@Component
@RequiredArgsConstructor
public class DomainEventOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventOutboxPublisher.class);

    private final DomainEventOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${serviya.events.outbox.batch-size:100}")
    private int batchSize;

    @Value("${serviya.events.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    @Transactional
    public int publishPendingBatch() {
        List<DomainEventOutboxEntity> batch = repository.claimBatch(batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            rabbitTemplate.invoke(operations -> {
                for (DomainEventOutboxEntity row : batch) {
                    operations.send(row.getExchange(), row.getRoutingKey(), toAmqpMessage(row));
                }
                operations.waitForConfirmsOrDie(confirmTimeoutMs);
                return null;
            });
        } catch (AmqpException ex) {
            String error = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            batch.forEach(row -> {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(error);
            });
            int attempts = batch.get(0).getAttempts();
            if (attempts == 1 || attempts % 60 == 0) {
                log.warn("Could not publish {} domain event(s) to RabbitMQ (attempt {}); they stay in the outbox: {}",
                        batch.size(), attempts, error);
            } else {
                log.debug("Domain event outbox publish attempt {} failed: {}", attempts, error);
            }
            return 0;
        }
        repository.deleteAllInBatch(batch);
        return batch.size();
    }

    private static Message toAmqpMessage(DomainEventOutboxEntity row) {
        return MessageBuilder.withBody(row.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(row.getEventId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
