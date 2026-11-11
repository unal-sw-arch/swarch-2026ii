package com.parosurvivors.serviya.shared.events.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Sobre comun de los eventos de dominio que viajan por RabbitMQ.
 *
 * <p>Los nombres JSON son {@code snake_case} para que el contrato sea estable entre Java, Python
 * y cualquier otro consumidor. {@code event_version} es la version del esquema; no la version del
 * agregado.</p>
 */
public record EventEnvelope(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_version") int eventVersion,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("subject") String subject,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("causation_id") String causationId,
        @JsonProperty("data") Map<String, Object> data) {
}
