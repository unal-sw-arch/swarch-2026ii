package com.parosurvivors.serviya.notifications.application.api;

import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion del contrato publico de notifications sobre el caso de uso interno existente.
 *
 * <p>Traduce {@link Channel} (API) a {@link ChannelName} (dominio) sin exponer el enum interno a
 * otros modulos. El servicio interno sigue siendo el dueno de la logica de outbox.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationCommandApiFacade implements NotificationCommandApi {

    private final NotificationServicePort notificationServicePort;

    @Override
    public void notify(NotificationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("NotificationCommand is required");
        }
        notificationServicePort.notify(
                command.userId(),
                command.type(),
                command.title(),
                command.message(),
                command.entityType(),
                command.entityId(),
                toInternalChannels(command.channels()),
                command.protectedData());
    }

    private Set<ChannelName> toInternalChannels(Set<Channel> channels) {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .map(channel -> ChannelName.valueOf(channel.name()))
                .collect(Collectors.toSet());
    }
}
