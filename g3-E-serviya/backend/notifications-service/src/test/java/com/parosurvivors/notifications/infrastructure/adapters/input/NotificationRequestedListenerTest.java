package com.parosurvivors.notifications.infrastructure.adapters.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parosurvivors.notifications.application.dto.command.NotifyCommand;
import com.parosurvivors.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.ChannelName;
import com.parosurvivors.notifications.infrastructure.messaging.PayloadSealer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Traducción mensaje → comando y política de errores del consumidor. El JSON de ejemplo
 * ({@code contracts/notification-requested.v1.json}) es el MISMO archivo que valida el monolito al producir.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRequestedListenerTest {

    @Mock private NotificationServicePort notificationService;
    @Mock private NotificationPersistencePort notificationPort;

    private NotificationRequestedListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new NotificationRequestedListener(notificationService, notificationPort,
                new PayloadSealer(com.parosurvivors.notifications.infrastructure.messaging.PayloadSealerTest.KEY),
                mapper);
    }

    private static byte[] sample() throws IOException {
        try (InputStream in = NotificationRequestedListenerTest.class
                .getResourceAsStream("/contracts/notification-requested.v1.json")) {
            return in.readAllBytes();
        }
    }

    @Test
    void contractSample_isTranslatedToCommand_withSealedContentOpened() throws IOException {
        NotifyCommand command = listener.toCommand(sample());

        assertThat(command.sourceEventId()).isEqualTo("6f1c2a0e-6c43-4a5e-9a51-0d2f6f7c9b10");
        assertThat(command.userId()).isEqualTo(42L);
        assertThat(command.type()).isEqualTo("password_reset");
        assertThat(command.entityType()).isEqualTo("USER");
        assertThat(command.channels()).containsExactly(ChannelName.EMAIL);
        assertThat(command.recipientEmail()).isEqualTo("ana@serviya.com");
        assertThat(command.protectedData()).containsEntry("actionUrl", "https://serviya.test/reset?token=abc");
    }

    @Test
    void unknownChannel_isIgnored_restOfMessageIsKept() {
        String json = """
                {"schemaVersion":1,"eventId":"e-1","userId":1,"type":"t","title":"T","message":"M",
                 "channels":["INTERNAL","PUSH"],"futureField":"ignored"}""";

        NotifyCommand command = listener.toCommand(json.getBytes(StandardCharsets.UTF_8));

        assertThat(command.channels()).containsExactly(ChannelName.INTERNAL);
        assertThat(command.recipientEmail()).isNull();
        assertThat(command.protectedData()).isEmpty();
    }

    @Test
    void malformedJson_isRejectedWithoutRequeue() {
        assertThatThrownBy(() -> listener.toCommand("{not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void unsupportedSchemaVersion_isRejectedWithoutRequeue() {
        String json = """
                {"schemaVersion":2,"eventId":"e-1","userId":1,"type":"t","title":"T","message":"M"}""";
        assertThatThrownBy(() -> listener.toCommand(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void sealedWithAnotherKey_isRejectedWithoutRequeue() {
        String json = """
                {"schemaVersion":1,"eventId":"e-1","userId":1,"type":"t","title":"T","message":"M",
                 "sealed":"AQIDBAUGBwgJCgsMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""";
        assertThatThrownBy(() -> listener.toCommand(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void concurrentDuplicate_uniqueViolation_isAcknowledged() throws IOException {
        when(notificationService.notify(any())).thenThrow(new DataIntegrityViolationException("uk"));
        when(notificationPort.existsBySourceEventId("6f1c2a0e-6c43-4a5e-9a51-0d2f6f7c9b10")).thenReturn(true);

        listener.onNotificationRequested(new Message(sample(), new MessageProperties()));
        // Sin excepción: el mensaje se confirma (ack) como duplicado.
    }
}
