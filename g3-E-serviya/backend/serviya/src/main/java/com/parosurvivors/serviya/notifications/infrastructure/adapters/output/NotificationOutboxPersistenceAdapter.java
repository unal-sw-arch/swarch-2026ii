package com.parosurvivors.serviya.notifications.infrastructure.adapters.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.serviya.notifications.application.dto.NotificationRequest;
import com.parosurvivors.serviya.notifications.application.ports.output.NotificationOutboxPort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.notifications.infrastructure.entities.NotificationOutboxEntity;
import com.parosurvivors.serviya.notifications.infrastructure.messaging.NotificationRequestedMessage;
import com.parosurvivors.serviya.notifications.infrastructure.messaging.NotificationRequestedMessage.SealedContent;
import com.parosurvivors.serviya.notifications.infrastructure.messaging.PayloadSealer;
import com.parosurvivors.serviya.notifications.infrastructure.repositories.NotificationOutboxRepository;
import java.time.LocalDateTime;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serializa la petición al contrato {@code notification.requested.v1} (sellando lo sensible) y la guarda en
 * la tabla outbox. Corre dentro de la transacción del llamador.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPersistenceAdapter implements NotificationOutboxPort {

    private final NotificationOutboxRepository repository;
    private final PayloadSealer payloadSealer;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(NotificationRequest request) {
        NotificationOutboxEntity row = new NotificationOutboxEntity();
        row.setEventId(request.eventId());
        row.setPayload(toJson(request));
        row.setCreatedAt(LocalDateTime.now());
        row.setAttempts(0);
        repository.save(row);
    }

    String toJson(NotificationRequest request) {
        try {
            NotificationRequestedMessage message = new NotificationRequestedMessage(
                    NotificationRequestedMessage.SCHEMA_VERSION,
                    request.eventId(),
                    request.occurredAt(),
                    request.userId(),
                    request.type(),
                    request.title(),
                    request.message(),
                    request.entityType(),
                    request.entityId(),
                    request.channels().stream().map(ChannelName::name).collect(Collectors.toCollection(TreeSet::new)),
                    seal(request));
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize notification.requested " + request.eventId(), ex);
        }
    }

    /** Solo se sella (y viaja) lo que existe: sin correo ni protectedData, el campo va null. */
    private String seal(NotificationRequest request) throws JsonProcessingException {
        boolean hasEmail = request.recipientEmail() != null;
        boolean hasProtected = request.protectedData() != null && !request.protectedData().isEmpty();
        if (!hasEmail && !hasProtected) {
            return null;
        }
        String plaintext = objectMapper.writeValueAsString(
                new SealedContent(request.recipientEmail(), request.protectedData()));
        return payloadSealer.seal(plaintext);
    }
}
