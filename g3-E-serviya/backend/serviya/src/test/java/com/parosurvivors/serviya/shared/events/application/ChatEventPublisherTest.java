package com.parosurvivors.serviya.shared.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.parosurvivors.serviya.shared.contract.IntegrationEvents;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventOutboxPort;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    @Mock private DomainEventOutboxPort outbox;
    @InjectMocks private ChatEventPublisher publisher;

    @Test
    void requestCreatedUsesRequestsExchangeAndSubject() {
        publisher.requestCreated(10L, 1L, 2L, 3L, "PENDING", LocalDateTime.of(2026, 1, 1, 10, 0));

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(outbox).enqueue(
                eq(IntegrationEvents.REQUESTS_EXCHANGE),
                eq(IntegrationEvents.REQUEST_CREATED),
                eq(IntegrationEvents.REQUEST_CREATED),
                eq("service_request/10"),
                data.capture());
        assertThat(data.getValue()).containsEntry("request_id", 10L)
                .containsEntry("status", "PENDING");
    }

    @Test
    void userSuspendedUsesUsersExchangeAndSubject() {
        publisher.userSuspended(7L, "spam");

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(outbox).enqueue(
                eq(IntegrationEvents.USERS_EXCHANGE),
                eq(IntegrationEvents.USER_SUSPENDED),
                eq(IntegrationEvents.USER_SUSPENDED),
                eq("user/7"),
                data.capture());
        assertThat(data.getValue()).containsEntry("user_id", 7L)
                .containsEntry("reason", "spam");
    }
}
