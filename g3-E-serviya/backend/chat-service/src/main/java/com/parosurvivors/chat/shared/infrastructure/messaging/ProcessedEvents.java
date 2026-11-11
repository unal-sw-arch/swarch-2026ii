package com.parosurvivors.chat.shared.infrastructure.messaging;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Registro de eventos ya consumidos (RNF-CHAT-09). Los manejadores son idempotentes por sí mismos (abrir una
 * conversación que ya existe, aplicar un estado igual o más viejo, etc.); esta tabla evita reprocesar y deja
 * constancia. Se marca DESPUÉS de procesar con éxito: si el proceso muere en medio, la redelivery vuelve a
 * ejecutar un manejador idempotente en vez de perder el evento.
 */
@Component
public class ProcessedEvents {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    ProcessedEvents(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean alreadyProcessed(String eventId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        return n != null && n > 0;
    }

    /** {@code INSERT IGNORE}: dos copias concurrentes del mismo evento no fallan, solo la primera inserta. */
    public void markProcessed(String eventId, String eventType) {
        jdbc.update("INSERT IGNORE INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)",
                eventId, eventType, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
