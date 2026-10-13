package com.parosurvivors.serviya.notifications.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parosurvivors.serviya.notifications.application.dto.NotificationRequest;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.notifications.infrastructure.entities.NotificationOutboxEntity;
import com.parosurvivors.serviya.notifications.infrastructure.messaging.PayloadSealer;
import com.parosurvivors.serviya.notifications.infrastructure.messaging.PayloadSealerTest;
import com.parosurvivors.serviya.notifications.infrastructure.repositories.NotificationOutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El JSON que sale por el outbox respeta el contrato {@code notification.requested.v1} que consume
 * notifications-service (mismos nombres de campo que su ejemplo {@code contracts/notification-requested.v1.json})
 * y no lleva datos sensibles en claro.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxPersistenceAdapterTest {

    @Mock private NotificationOutboxRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // como el ObjectMapper de Spring Boot

    private NotificationOutboxPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationOutboxPersistenceAdapter(repository, new PayloadSealer(PayloadSealerTest.KEY),
                objectMapper);
    }

    private static NotificationRequest request(String email, Map<String, String> protectedData) {
        return new NotificationRequest("6f1c2a0e-6c43-4a5e-9a51-0d2f6f7c9b10", Instant.parse("2026-10-12T21:00:00Z"),
                42L, "password_reset", "Restablece tu contraseña", "Mensaje", "USER", 42L,
                Set.of(ChannelName.EMAIL, ChannelName.INTERNAL), email, protectedData);
    }

    @Test
    void enqueue_persistsContractJson_withSensitiveDataSealed() throws Exception {
        adapter.enqueue(request("ana@serviya.com", Map.of("actionUrl", "https://serviya.test/reset?token=abc")));

        ArgumentCaptor<NotificationOutboxEntity> captor = ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(repository).save(captor.capture());
        NotificationOutboxEntity row = captor.getValue();
        assertThat(row.getEventId()).isEqualTo("6f1c2a0e-6c43-4a5e-9a51-0d2f6f7c9b10");
        assertThat(row.getAttempts()).isZero();

        // Nada sensible en claro en la fila del outbox.
        assertThat(row.getPayload()).doesNotContain("ana@serviya.com").doesNotContain("token=abc");

        JsonNode json = objectMapper.readTree(row.getPayload());
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("eventId").asText()).isEqualTo("6f1c2a0e-6c43-4a5e-9a51-0d2f6f7c9b10");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-12T21:00:00Z");
        assertThat(json.get("userId").asLong()).isEqualTo(42L);
        assertThat(json.get("type").asText()).isEqualTo("password_reset");
        assertThat(json.get("entityType").asText()).isEqualTo("USER");
        assertThat(json.get("channels").toString()).isEqualTo("[\"EMAIL\",\"INTERNAL\"]");

        String opened = PayloadSealerTest.open(PayloadSealerTest.KEY, json.get("sealed").asText());
        assertThat(objectMapper.readTree(opened).get("recipientEmail").asText()).isEqualTo("ana@serviya.com");
        assertThat(objectMapper.readTree(opened).get("protectedData").get("actionUrl").asText())
                .isEqualTo("https://serviya.test/reset?token=abc");
    }

    @Test
    void enqueue_withoutSensitiveData_omitsTheSealedField() throws Exception {
        adapter.enqueue(request(null, Map.of()));

        ArgumentCaptor<NotificationOutboxEntity> captor = ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(repository).save(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().getPayload()).get("sealed").isNull()).isTrue();
    }
}
