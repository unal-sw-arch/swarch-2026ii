package com.parosurvivors.notifications.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.notifications.application.events.EmailDeliveryRequestedEvent;
import com.parosurvivors.notifications.application.ports.output.CoordinationAvailabilityPort;
import com.parosurvivors.notifications.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.notifications.application.ports.output.EmailPort;
import com.parosurvivors.notifications.application.ports.output.NotificationChannelPersistencePort;
import com.parosurvivors.notifications.application.ports.output.NotificationDeliveryPersistencePort;
import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.DeliveryStatus;
import com.parosurvivors.notifications.domain.Notification;
import com.parosurvivors.notifications.domain.NotificationChannel;
import com.parosurvivors.notifications.domain.NotificationDelivery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas de entrega: contabilización de intentos, envío EMAIL diferido, reintento de las FAILED con
 * {@code attempts < maxDeliveryAttempts} y modo degradado D-07/1a.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock private NotificationDeliveryPersistencePort deliveryPort;
    @Mock private NotificationChannelPersistencePort channelPort;
    @Mock private NotificationPersistencePort notificationPort;
    @Mock private ObjectProvider<EmailPort> emailPortProvider;
    @Mock private DomainEventPublisherPort eventPublisher;
    @Mock private ObjectProvider<CoordinationAvailabilityPort> coordinationAvailabilityProvider;
    @Mock private EmailPort emailPort;

    private NotificationDeliveryService service;

    private static final int MAX_ATTEMPTS = 3;

    @BeforeEach
    void setUp() {
        // Construcción explícita (no @InjectMocks): hay dos ObjectProvider del mismo tipo borrado y la
        // inyección por nombre de Mockito es ambigua.
        service = new NotificationDeliveryService(
                deliveryPort, channelPort, notificationPort, emailPortProvider, eventPublisher,
                coordinationAvailabilityProvider);
        ReflectionTestUtils.setField(service, "maxDeliveryAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(service, "retryBatchSize", 100);
    }

    private NotificationChannel channel(int id, String name) {
        return NotificationChannel.builder().id(id).name(name).build();
    }

    private Notification notification(long id, String type, String email) {
        return Notification.builder().id(id).userId(5L).notificationType(type)
                .title("Título").message("Mensaje").recipientEmail(email).build();
    }

    @Test
    void deliver_internal_marksSentAndCountsFirstAttempt() {
        when(channelPort.findById(1)).thenReturn(Optional.of(channel(1, "INTERNAL")));
        when(deliveryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationDelivery result = service.deliver(10L, 1L, Map.of());

        assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.getAttempts()).isEqualTo(1);
    }

    @Test
    void deliver_email_isDeferred_savesPendingAndPublishesEvent() {
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(deliveryPort.save(any())).thenAnswer(inv -> {
            NotificationDelivery d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });

        NotificationDelivery result = service.deliver(10L, 2L, Map.of("actionUrl", "https://x"));

        // No se envía en línea: queda PENDING sin intentos, y el envío se delega vía evento AFTER_COMMIT.
        assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(result.getAttempts()).isZero();
        verify(emailPortProvider, never()).getIfUnique();
        ArgumentCaptor<EmailDeliveryRequestedEvent> captor = ArgumentCaptor.forClass(EmailDeliveryRequestedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().deliveryId()).isEqualTo(99L);
        assertThat(captor.getValue().protectedData()).containsEntry("actionUrl", "https://x");
    }

    @Test
    void sendPendingEmail_sendsToRecipientCarriedInTheNotification() {
        NotificationDelivery pending = NotificationDelivery.builder()
                .id(7L).notificationId(10L).channelId(2)
                .deliveryStatus(DeliveryStatus.PENDING).attempts(0).build();
        when(deliveryPort.findById(7L)).thenReturn(Optional.of(pending));
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(emailPortProvider.getIfUnique()).thenReturn(emailPort);
        when(notificationPort.findById(10L)).thenReturn(Optional.of(notification(10L, "welcome", "ana@serviya.com")));
        when(emailPort.send(eq("ana@serviya.com"), eq("welcome"), eq("Título"), eq("Mensaje"), any())).thenReturn(true);

        service.sendPendingEmail(7L, Map.of());

        assertThat(pending.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(deliveryPort).update(pending);
    }

    @Test
    void sendPendingEmail_withoutRecipientEmail_marksFailedWithoutCallingProvider() {
        NotificationDelivery pending = NotificationDelivery.builder()
                .id(7L).notificationId(10L).channelId(2)
                .deliveryStatus(DeliveryStatus.PENDING).attempts(0).build();
        when(deliveryPort.findById(7L)).thenReturn(Optional.of(pending));
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(emailPortProvider.getIfUnique()).thenReturn(emailPort);
        when(notificationPort.findById(10L)).thenReturn(Optional.of(notification(10L, "welcome", null)));

        service.sendPendingEmail(7L, Map.of());

        assertThat(pending.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(emailPort, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void sendPendingEmail_emailWithoutPort_marksFailedAndCountsAttempt() {
        NotificationDelivery pending = NotificationDelivery.builder()
                .id(7L).notificationId(10L).channelId(2)
                .deliveryStatus(DeliveryStatus.PENDING).attempts(0).build();
        when(deliveryPort.findById(7L)).thenReturn(Optional.of(pending));
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(emailPortProvider.getIfUnique()).thenReturn(null);

        service.sendPendingEmail(7L, Map.of());

        assertThat(pending.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(pending.getAttempts()).isEqualTo(1);
        verify(deliveryPort).update(pending);
    }

    @Test
    void sendPendingEmail_coordinationDown_defersEmail_marksFailed() {
        NotificationDelivery pending = NotificationDelivery.builder()
                .id(7L).notificationId(10L).channelId(2)
                .deliveryStatus(DeliveryStatus.PENDING).attempts(0).build();
        when(deliveryPort.findById(7L)).thenReturn(Optional.of(pending));
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(coordinationAvailabilityProvider.getIfAvailable()).thenReturn(() -> false);

        service.sendPendingEmail(7L, Map.of());

        assertThat(pending.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(emailPortProvider, never()).getIfUnique();
    }

    @Test
    void retry_resendsFailedBelowMax_andSucceedsOnInternal() {
        NotificationDelivery failed = NotificationDelivery.builder()
                .id(5L).notificationId(10L).channelId(1)
                .deliveryStatus(DeliveryStatus.FAILED).attempts(1).build();
        when(deliveryPort.claimForRetry(DeliveryStatus.FAILED, MAX_ATTEMPTS, 100)).thenReturn(List.of(failed));
        when(notificationPort.findById(10L)).thenReturn(Optional.of(notification(10L, "welcome", null)));
        when(channelPort.findById(1)).thenReturn(Optional.of(channel(1, "INTERNAL")));

        int retried = service.retryFailedDeliveries();

        assertThat(retried).isEqualTo(1);
        assertThat(failed.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(failed.getAttempts()).isEqualTo(2);
        verify(deliveryPort).update(failed);
    }

    @Test
    void retry_emailWithoutPort_staysFailed_butCountsTheAttempt() {
        NotificationDelivery failed = NotificationDelivery.builder()
                .id(6L).notificationId(11L).channelId(2)
                .deliveryStatus(DeliveryStatus.FAILED).attempts(2).build();
        when(deliveryPort.claimForRetry(DeliveryStatus.FAILED, MAX_ATTEMPTS, 100)).thenReturn(List.of(failed));
        when(notificationPort.findById(11L)).thenReturn(Optional.of(notification(11L, "welcome", "a@b.co")));
        when(channelPort.findById(2)).thenReturn(Optional.of(channel(2, "EMAIL")));
        when(emailPortProvider.getIfUnique()).thenReturn(null);

        int retried = service.retryFailedDeliveries();

        assertThat(retried).isEqualTo(1);
        assertThat(failed.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3); // agotó el máximo: no volverá a reintentarse
        verify(deliveryPort).update(failed);
    }

    @Test
    void retry_skipsPasswordReset_becauseItIsWorthlessWithoutTheLink() {
        NotificationDelivery failed = NotificationDelivery.builder()
                .id(8L).notificationId(12L).channelId(2)
                .deliveryStatus(DeliveryStatus.FAILED).attempts(1).build();
        when(deliveryPort.claimForRetry(DeliveryStatus.FAILED, MAX_ATTEMPTS, 100)).thenReturn(List.of(failed));
        when(notificationPort.findById(12L)).thenReturn(Optional.of(notification(12L, "password_reset", "a@b.co")));

        int retried = service.retryFailedDeliveries();

        assertThat(retried).isZero();
        assertThat(failed.getAttempts()).isEqualTo(1);
        verify(deliveryPort, never()).update(any());
    }

    @Test
    void retry_coordinationDown_doesNotClaimNorBurnAttempts() {
        when(coordinationAvailabilityProvider.getIfAvailable()).thenReturn(() -> false);

        int retried = service.retryFailedDeliveries();

        assertThat(retried).isZero();
        verify(deliveryPort, never()).claimForRetry(any(), anyInt(), anyInt());
    }

    @Test
    void retry_noneEligible_doesNothing() {
        when(deliveryPort.claimForRetry(DeliveryStatus.FAILED, MAX_ATTEMPTS, 100)).thenReturn(List.of());

        int retried = service.retryFailedDeliveries();

        assertThat(retried).isZero();
        verify(deliveryPort, never()).update(any());
        verify(channelPort, never()).findById(any());
    }
}
