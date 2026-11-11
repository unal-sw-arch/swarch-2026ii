package com.parosurvivors.chat.shared.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Emite el comando {@code notification.requested.v1} por el contrato EXISTENTE de ServiYa (exchange
 * {@code serviya.notifications}), igual que lo hace el monolito (ADR-011). Va por el mismo outbox, así que sale
 * en la misma transacción que el mensaje.
 *
 * <p>Formato v1 (ver NotificationRequestedMessage en notifications-service): camelCase, {@code schemaVersion=1},
 * canales {@code INTERNAL|EMAIL}. El chat no manda {@code sealed}: no lleva correo ni datos sensibles.</p>
 */
@Component
class NotificationCommandAdapter implements NotificationRequester {

    static final String EXCHANGE = "serviya.notifications";
    static final String ROUTING_KEY = "notification.requested.v1";
    static final int SCHEMA_VERSION = 1;

    private final OutboxWriter writer;
    private final ObjectMapper mapper;
    private final Clock clock;

    NotificationCommandAdapter(OutboxWriter writer, ObjectMapper mapper, Clock clock) {
        this.writer = writer;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void request(NotificationRequest r) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("schemaVersion", SCHEMA_VERSION);
        message.put("eventId", eventId);
        message.put("occurredAt", clock.instant().toString());
        message.put("userId", r.recipientId());
        message.put("type", r.type());
        message.put("title", r.title());
        message.put("message", r.message());
        message.put("entityType", r.entityType());
        message.put("entityId", r.entityId());
        message.put("channels", new TreeSet<>(r.channels()));
        message.put("sealed", null);
        try {
            writer.insert(eventId, EXCHANGE, ROUTING_KEY, mapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el comando de notificación", e);
        }
    }
}
