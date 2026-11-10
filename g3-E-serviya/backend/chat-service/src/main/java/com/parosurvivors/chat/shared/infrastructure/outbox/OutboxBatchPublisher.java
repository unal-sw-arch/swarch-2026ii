package com.parosurvivors.chat.shared.infrastructure.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicador de lotes del outbox (ADR-009): publica en RabbitMQ las filas pendientes con publisher confirms y las marca como
 * publicadas SOLO cuando el broker confirma. Entrega al menos una vez: si el commit del marcado falla tras la
 * confirmación, el lote se republica y los consumidores lo descartan por event_id.
 *
 * <p>Sin lock distribuido: el lote se reclama con {@code FOR UPDATE SKIP LOCKED}, así varias instancias del chat
 * pueden correr el relay a la vez sin publicar dos veces la misma fila. Con el broker caído las filas esperan
 * (anotando el error) y se reintentan en la siguiente pasada: el negocio no se entera.</p>
 */
@Component
public class OutboxBatchPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxBatchPublisher.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Value("${serviya.chat.outbox.batch-size:100}")
    private int batchSize;

    @Value("${serviya.chat.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    OutboxBatchPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit, Clock clock) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.clock = clock;
    }

    private record Row(long id, String eventId, String exchange, String routingKey, String payload, int attempts) {
    }

    /** Publica un lote. Devuelve cuántas filas se publicaron (0 si no había o si el broker falló). */
    @Transactional
    public int publishPendingBatch() {
        List<Row> batch = jdbc.query("""
                SELECT id, event_id, exchange_name, routing_key, payload, attempts
                  FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Row(rs.getLong("id"), rs.getString("event_id"), rs.getString("exchange_name"),
                rs.getString("routing_key"), rs.getString("payload"), rs.getInt("attempts")), batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            rabbit.invoke(ops -> {
                for (Row row : batch) {
                    ops.send(row.exchange(), row.routingKey(), toMessage(row));
                }
                ops.waitForConfirmsOrDie(confirmTimeoutMs); // nack o timeout → AmqpException
                return null;
            });
        } catch (AmqpException ex) {
            String error = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            for (Row row : batch) {
                jdbc.update("UPDATE outbox_events SET attempts = attempts + 1 WHERE id = ?", row.id());
            }
            int attempts = batch.get(0).attempts() + 1;
            // El relay corre cada segundo: durante una caída larga, avisar al primer fallo y luego ~1 vez/min.
            if (attempts == 1 || attempts % 60 == 0) {
                log.warn("No se pudieron publicar {} evento(s) en RabbitMQ (intento {}); quedan en el outbox: {}",
                        batch.size(), attempts, error);
            } else {
                log.debug("Intento {} de publicar el outbox falló: {}", attempts, error);
            }
            return 0;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (Row row : batch) {
            jdbc.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", now, row.id());
        }
        return batch.size();
    }

    private static Message toMessage(Row row) {
        return MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(row.eventId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
