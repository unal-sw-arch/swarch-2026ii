package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import com.parosurvivors.serviya.notifications.infrastructure.entities.NotificationOutboxEntity;
import com.parosurvivors.serviya.notifications.infrastructure.repositories.NotificationOutboxRepository;
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
 * Relay del outbox: publica en RabbitMQ las filas pendientes y las borra cuando el broker CONFIRMA
 * (publisher confirms). Garantía resultante: entrega "al menos una vez" — si el commit del borrado falla
 * tras la confirmación, el lote se republica y notifications-service lo descarta por {@code eventId}.
 *
 * <p>Si el broker no está disponible, las filas se quedan (anotando el error) y se reintentan en la
 * siguiente pasada: una caída de RabbitMQ o de notifications-service NO afecta a las operaciones de negocio,
 * solo retrasa las notificaciones.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${serviya.notifications.outbox.batch-size:100}")
    private int batchSize;

    @Value("${serviya.notifications.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    /** Publica un lote. Devuelve cuántas filas se publicaron (0 si no había o si el broker falló). */
    @Transactional
    public int publishPendingBatch() {
        List<NotificationOutboxEntity> batch = repository.claimBatch(batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            rabbitTemplate.invoke(operations -> {
                for (NotificationOutboxEntity row : batch) {
                    operations.send(NotificationMessagingConfig.EXCHANGE, NotificationMessagingConfig.ROUTING_KEY,
                            toAmqpMessage(row));
                }
                operations.waitForConfirmsOrDie(confirmTimeoutMs); // nack o timeout → AmqpException
                return null;
            });
        } catch (AmqpException ex) {
            String error = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            batch.forEach(row -> {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(error);
            });
            // El relay corre cada segundo: durante una caída larga, avisar al primer fallo y luego ~1 vez/min.
            int attempts = batch.get(0).getAttempts();
            if (attempts == 1 || attempts % 60 == 0) {
                log.warn("Could not publish {} notification(s) to RabbitMQ (attempt {}); they stay in the outbox: {}",
                        batch.size(), attempts, error);
            } else {
                log.debug("Outbox publish attempt {} failed: {}", attempts, error);
            }
            return 0;
        }
        repository.deleteAllInBatch(batch);
        return batch.size();
    }

    private static Message toAmqpMessage(NotificationOutboxEntity row) {
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
