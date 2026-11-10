package com.parosurvivors.serviya.notifications.application.api;

import static org.mockito.Mockito.verify;

import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCommandApiFacadeTest {

    @Mock
    private NotificationServicePort notificationServicePort;

    @InjectMocks
    private NotificationCommandApiFacade facade;

    @Test
    void mapsContractChannelsToInternalChannelNames() {
        NotificationCommand command = new NotificationCommand(
                9L,
                "request_accepted",
                "Solicitud aceptada",
                "Tu solicitud fue aceptada",
                "SERVICE_REQUEST",
                42L,
                Set.of(Channel.INTERNAL, Channel.EMAIL),
                Map.of("name", "Ana"));

        facade.notify(command);

        verify(notificationServicePort).notify(
                9L,
                "request_accepted",
                "Solicitud aceptada",
                "Tu solicitud fue aceptada",
                "SERVICE_REQUEST",
                42L,
                Set.of(ChannelName.INTERNAL, ChannelName.EMAIL),
                Map.of("name", "Ana"));
    }
}
