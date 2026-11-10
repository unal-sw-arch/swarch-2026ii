package com.parosurvivors.chat.shared.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Arma el sobre común acordado (decisiones-infraestructura-implementacion.md §4: event_id, event_type,
 * event_version, occurred_at, producer, subject, correlation_id, causation_id, data) y lo deja en el outbox
 * (ADR-009), en la transacción del llamador.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    public static final String PRODUCER = "chat-service";
    public static final String CORRELATION_MDC_KEY = "correlationId";
    /** event_id del evento consumido que provocó este (lo fijan los consumidores); nulo si lo originó un usuario. */
    public static final String CAUSATION_MDC_KEY = "causationId";

    private final OutboxWriter writer;
    private final ObjectMapper mapper;
    private final Clock clock;

    OutboxEventPublisher(OutboxWriter writer, ObjectMapper mapper, Clock clock) {
        this.writer = writer;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void publish(String exchange, String routingKey, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        String correlationId = MDC.get(CORRELATION_MDC_KEY);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", eventId);
        envelope.put("event_type", routingKey);
        envelope.put("event_version", 1);
        envelope.put("occurred_at", clock.instant().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("subject", "conversation/" + data.get("conversation_id"));
        envelope.put("correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString());
        envelope.put("causation_id", MDC.get(CAUSATION_MDC_KEY));
        envelope.put("data", data);
        try {
            writer.insert(eventId, exchange, routingKey, mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + routingKey, e);
        }
    }
}
