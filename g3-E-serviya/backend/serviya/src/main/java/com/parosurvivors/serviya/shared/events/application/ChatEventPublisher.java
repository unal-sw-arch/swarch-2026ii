package com.parosurvivors.serviya.shared.events.application;

import com.parosurvivors.serviya.shared.contract.IntegrationEvents;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventOutboxPort;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publicador de los eventos de integracion que el chat consume o produce.
 *
 * <p>Vive en el kernel compartido y no conoce entidades de negocio: recibe datos primitivos. Asi
 * requests/users/profiles pueden publicar sin acoplarse al envelope ni a RabbitMQ.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final DomainEventOutboxPort outbox;

    public void requestCreated(Long requestId, Long clientId, Long offererId, Long serviceId,
            String status, LocalDateTime createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("request_id", requestId);
        data.put("client_id", clientId);
        data.put("offerer_id", offererId);
        data.put("service_id", serviceId);
        data.put("status", status);
        data.put("created_at", createdAt == null ? null : createdAt.toString());
        enqueue(IntegrationEvents.REQUESTS_EXCHANGE, IntegrationEvents.REQUEST_CREATED,
                "service_request/" + requestId, data);
    }

    public void requestStatusChanged(Long requestId, Long changedBy, String previousStatus,
            String status, LocalDateTime changedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("request_id", requestId);
        data.put("previous_status", previousStatus);
        data.put("status", status);
        data.put("changed_by", changedBy);
        data.put("changed_at", changedAt == null ? null : changedAt.toString());
        enqueue(IntegrationEvents.REQUESTS_EXCHANGE, IntegrationEvents.REQUEST_STATUS_CHANGED,
                "service_request/" + requestId, data);
    }

    public void userRegistered(Long userId, String displayName, String avatarUrl, String role) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("display_name", displayName);
        data.put("avatar_url", avatarUrl);
        data.put("roles", List.of(role));
        data.put("status", "ACTIVE");
        enqueue(IntegrationEvents.USERS_EXCHANGE, IntegrationEvents.USER_REGISTERED,
                "user/" + userId, data);
    }

    public void userUpdated(Long userId, String displayName, String avatarUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("display_name", displayName);
        data.put("avatar_url", avatarUrl);
        enqueue(IntegrationEvents.USERS_EXCHANGE, IntegrationEvents.USER_UPDATED,
                "user/" + userId, data);
    }

    public void userSuspended(Long userId, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("reason", reason);
        enqueue(IntegrationEvents.USERS_EXCHANGE, IntegrationEvents.USER_SUSPENDED,
                "user/" + userId, data);
    }

    public void userReactivated(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        enqueue(IntegrationEvents.USERS_EXCHANGE, IntegrationEvents.USER_REACTIVATED,
                "user/" + userId, data);
    }

    public void userDeleted(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        enqueue(IntegrationEvents.USERS_EXCHANGE, IntegrationEvents.USER_DELETED,
                "user/" + userId, data);
    }

    private void enqueue(String exchange, String eventType, String subject, Map<String, Object> data) {
        outbox.enqueue(exchange, eventType, eventType, subject, data);
    }
}
