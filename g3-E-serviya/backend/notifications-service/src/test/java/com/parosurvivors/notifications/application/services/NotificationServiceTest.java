package com.parosurvivors.notifications.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.notifications.application.dto.command.NotifyCommand;
import com.parosurvivors.notifications.application.ports.input.NotificationChannelServicePort;
import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.ChannelName;
import com.parosurvivors.notifications.domain.Notification;
import com.parosurvivors.notifications.domain.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationPersistencePort notificationPort;
    @Mock private NotificationDeliveryServicePort deliveryService;
    @Mock private NotificationChannelServicePort channelService;

    @InjectMocks private NotificationService service;

    private static NotifyCommand command(String eventId, Set<ChannelName> channels) {
        return new NotifyCommand(eventId, 5L, "welcome", "Hola", "Bienvenido", "USER", 5L,
                channels, "ana@serviya.com", Map.of("actionUrl", "https://x"));
    }

    @Test
    void notify_persistsNotificationWithEventIdAndRecipient_andDeliversPerChannel() {
        when(notificationPort.existsBySourceEventId("evt-1")).thenReturn(false);
        when(notificationPort.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });
        when(channelService.getChannels()).thenReturn(List.of(
                NotificationChannel.builder().id(1).name("INTERNAL").build(),
                NotificationChannel.builder().id(2).name("EMAIL").build()));

        boolean processed = service.notify(command("evt-1", Set.of(ChannelName.INTERNAL, ChannelName.EMAIL)));

        assertThat(processed).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationPort).save(saved.capture());
        assertThat(saved.getValue().getSourceEventId()).isEqualTo("evt-1");
        assertThat(saved.getValue().getRecipientEmail()).isEqualTo("ana@serviya.com");
        verify(deliveryService).deliver(10L, 1L, Map.of("actionUrl", "https://x"));
        verify(deliveryService).deliver(10L, 2L, Map.of("actionUrl", "https://x"));
    }

    @Test
    void notify_withoutChannels_defaultsToInternal() {
        when(notificationPort.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(11L);
            return n;
        });
        when(channelService.getChannels()).thenReturn(List.of(
                NotificationChannel.builder().id(1).name("INTERNAL").build(),
                NotificationChannel.builder().id(2).name("EMAIL").build()));

        service.notify(command("evt-2", null));

        verify(deliveryService).deliver(eq(11L), eq(1L), any());
        verify(deliveryService, never()).deliver(eq(11L), eq(2L), any());
    }

    @Test
    void notify_duplicateEvent_isIgnored() {
        when(notificationPort.existsBySourceEventId("evt-dup")).thenReturn(true);

        boolean processed = service.notify(command("evt-dup", Set.of(ChannelName.INTERNAL)));

        assertThat(processed).isFalse();
        verify(notificationPort, never()).save(any());
        verify(deliveryService, never()).deliver(anyLong(), anyLong(), any());
    }
}
