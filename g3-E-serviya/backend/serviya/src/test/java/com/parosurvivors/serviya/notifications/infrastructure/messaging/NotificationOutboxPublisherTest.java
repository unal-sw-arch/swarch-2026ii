package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.infrastructure.entities.NotificationOutboxEntity;
import com.parosurvivors.serviya.notifications.infrastructure.repositories.NotificationOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Relay del outbox: borra solo lo que el broker confirmó; ante fallo conserva las filas para reintentar. */
@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherTest {

    @Mock private NotificationOutboxRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RabbitOperations operations;

    private NotificationOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationOutboxPublisher(repository, rabbitTemplate);
        ReflectionTestUtils.setField(publisher, "batchSize", 100);
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 5000L);
    }

    private static NotificationOutboxEntity row(long id, String eventId) {
        NotificationOutboxEntity row = new NotificationOutboxEntity();
        row.setId(id);
        row.setEventId(eventId);
        row.setPayload("{\"eventId\":\"" + eventId + "\"}");
        row.setAttempts(0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void brokerRunsCallback() {
        when(rabbitTemplate.invoke(any())).thenAnswer(inv ->
                ((RabbitOperations.OperationsCallback<Object>) inv.getArgument(0)).doInRabbit(operations));
    }

    @Test
    void publishesEachRowWithItsEventIdAsMessageId_andDeletesThemAfterConfirm() {
        List<NotificationOutboxEntity> batch = List.of(row(1, "e-1"), row(2, "e-2"));
        when(repository.claimBatch(100)).thenReturn(batch);
        brokerRunsCallback();

        int published = publisher.publishPendingBatch();

        assertThat(published).isEqualTo(2);
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(operations, org.mockito.Mockito.times(2)).send(eq(NotificationMessagingConfig.EXCHANGE),
                eq(NotificationMessagingConfig.ROUTING_KEY), sent.capture());
        assertThat(sent.getAllValues().get(0).getMessageProperties().getMessageId()).isEqualTo("e-1");
        assertThat(new String(sent.getAllValues().get(1).getBody(), StandardCharsets.UTF_8)).contains("e-2");
        verify(operations).waitForConfirmsOrDie(5000L);
        verify(repository).deleteAllInBatch(batch);
    }

    @Test
    void brokerFailure_keepsRowsAndRecordsTheError() {
        NotificationOutboxEntity pending = row(1, "e-1");
        when(repository.claimBatch(100)).thenReturn(List.of(pending));
        brokerRunsCallback();
        doThrow(new AmqpTimeoutException("no confirm")).when(operations).waitForConfirmsOrDie(anyLong());

        int published = publisher.publishPendingBatch();

        assertThat(published).isZero();
        verify(repository, never()).deleteAllInBatch(any());
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getLastError()).contains("no confirm");
    }

    @Test
    void emptyOutbox_doesNotTouchTheBroker() {
        when(repository.claimBatch(100)).thenReturn(List.of());

        assertThat(publisher.publishPendingBatch()).isZero();
        verify(rabbitTemplate, never()).invoke(any());
    }
}
