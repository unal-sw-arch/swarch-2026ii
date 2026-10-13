package com.parosurvivors.serviya.notifications.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.dto.NotificationRequest;
import com.parosurvivors.serviya.notifications.application.ports.output.NotificationOutboxPort;
import com.parosurvivors.serviya.notifications.application.ports.output.RecipientEmailPort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cliente de notifications-service: arma el comando y lo deja en el outbox (no entrega nada). */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationOutboxPort outboxPort;
    @Mock private RecipientEmailPort recipientEmailPort;

    @InjectMocks private NotificationService service;

    private NotificationRequest enqueued() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(outboxPort).enqueue(captor.capture());
        return captor.getValue();
    }

    @Test
    void notify_withEmail_resolvesRecipientAndEnqueuesFullCommand() {
        when(recipientEmailPort.findEmail(5L)).thenReturn(Optional.of("ana@serviya.com"));

        service.notify(5L, "password_reset", "Restablece", "Mensaje", "USER", 5L,
                Set.of(ChannelName.EMAIL), Map.of("actionUrl", "https://x"));

        NotificationRequest request = enqueued();
        assertThat(request.eventId()).isNotBlank();
        assertThat(request.occurredAt()).isNotNull();
        assertThat(request.userId()).isEqualTo(5L);
        assertThat(request.type()).isEqualTo("password_reset");
        assertThat(request.channels()).containsExactly(ChannelName.EMAIL);
        assertThat(request.recipientEmail()).isEqualTo("ana@serviya.com");
        assertThat(request.protectedData()).containsEntry("actionUrl", "https://x");
    }

    @Test
    void notify_internalOnly_doesNotResolveNorShipTheEmail() {
        service.notify(5L, "role_acquired", "Nuevo rol", "Mensaje", "USER", 5L, null, Map.of());

        NotificationRequest request = enqueued();
        assertThat(request.recipientEmail()).isNull();
        assertThat(request.channels()).isEmpty(); // el servicio aplica el default INTERNAL
        verify(recipientEmailPort, never()).findEmail(any());
    }

    @Test
    void notify_generatesADistinctEventIdPerCall() {
        service.notify(1L, "t", "T", "M", null, null, null, null);
        service.notify(1L, "t", "T", "M", null, null, null, null);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(outboxPort, org.mockito.Mockito.times(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues().get(0).eventId()).isNotEqualTo(captor.getAllValues().get(1).eventId());
        assertThat(captor.getAllValues().get(0).protectedData()).isEmpty();
    }
}
