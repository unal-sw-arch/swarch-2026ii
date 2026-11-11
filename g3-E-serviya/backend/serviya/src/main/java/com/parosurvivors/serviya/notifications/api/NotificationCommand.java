package com.parosurvivors.serviya.notifications.api;

import java.util.Map;
import java.util.Set;

/**
 * Comando publico para solicitar una notificacion.
 *
 * <p>Es un contrato autocontenido: no expone {@code NotificationRequest} ni el enum interno
 * {@code ChannelName}. Los modulos llamadores solo conocen este record y {@link Channel}.</p>
 */
public record NotificationCommand(
        Long userId,
        String type,
        String title,
        String message,
        String entityType,
        Long entityId,
        Set<Channel> channels,
        Map<String, String> protectedData) {

    public NotificationCommand {
        channels = channels == null ? Set.of() : Set.copyOf(channels);
        protectedData = protectedData == null ? Map.of() : Map.copyOf(protectedData);
    }

    public static NotificationCommand internal(Long userId, String type, String title, String message) {
        return new NotificationCommand(userId, type, title, message, null, null,
                Set.of(Channel.INTERNAL), Map.of());
    }
}
