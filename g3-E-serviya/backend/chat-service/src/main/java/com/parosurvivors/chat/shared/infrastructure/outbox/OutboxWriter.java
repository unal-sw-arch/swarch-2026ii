package com.parosurvivors.chat.shared.infrastructure.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Inserta una fila del outbox. Exige transacción activa: el evento y el cambio de negocio confirman juntos. */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    OutboxWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(String eventId, String exchange, String routingKey, String payloadJson) {
        // LocalDateTime (no Timestamp): Timestamp reinterpretaría la hora en la zona de la JVM.
        jdbc.update("INSERT INTO outbox_events (event_id, exchange_name, routing_key, payload, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventId, exchange, routingKey, payloadJson, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
