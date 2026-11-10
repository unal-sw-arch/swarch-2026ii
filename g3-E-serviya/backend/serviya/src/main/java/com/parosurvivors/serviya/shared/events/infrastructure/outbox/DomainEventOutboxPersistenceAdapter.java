package com.parosurvivors.serviya.shared.events.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.shared.contract.IntegrationEvents;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventOutboxPort;
import com.parosurvivors.serviya.shared.events.domain.EventEnvelope;
import com.parosurvivors.serviya.shared.security.CorrelationContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serializa el envelope y lo guarda en el outbox. Corre dentro de la transaccion del llamador.
 */
@Component
@RequiredArgsConstructor
public class DomainEventOutboxPersistenceAdapter implements DomainEventOutboxPort {

    private final DomainEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(String exchange, String routingKey, String eventType, String subject,
            Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                eventType,
                IntegrationEvents.EVENT_SCHEMA_VERSION,
                Instant.now().toString(),
                IntegrationEvents.PRODUCER,
                subject,
                CorrelationContext.current(),
                null,
                data == null ? Map.of() : data);

        DomainEventOutboxEntity row = new DomainEventOutboxEntity();
        row.setEventId(eventId);
        row.setExchange(exchange);
        row.setRoutingKey(routingKey);
        row.setPayload(toJson(envelope));
        row.setCreatedAt(LocalDateTime.now());
        row.setAttempts(0);
        repository.save(row);
    }

    private String toJson(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize event " + envelope.eventId(), ex);
        }
    }
}
