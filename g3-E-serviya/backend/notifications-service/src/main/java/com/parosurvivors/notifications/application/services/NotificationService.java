package com.parosurvivors.notifications.application.services;

import com.parosurvivors.notifications.application.dto.command.NotifyCommand;
import com.parosurvivors.notifications.application.ports.input.NotificationChannelServicePort;
import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import com.parosurvivors.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.ChannelName;
import com.parosurvivors.notifications.domain.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationService implements NotificationServicePort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationPersistencePort notificationPersistencePort;
    private final NotificationDeliveryServicePort notificationDeliveryServicePort;
    private final NotificationChannelServicePort notificationChannelServicePort;

    @Override
    @Transactional
    public boolean notify(NotifyCommand command) {
        // Idempotencia del consumidor: el outbox+bus entregan "al menos una vez". Este chequeo cubre el caso
        // normal (redelivery tras un fallo); la carrera de dos copias simultáneas la cierra la UNIQUE de
        // notifications.source_event_id (el adaptador de entrada trata esa violación como duplicado).
        if (command.sourceEventId() != null
                && notificationPersistencePort.existsBySourceEventId(command.sourceEventId())) {
            log.info("Notification event {} already processed — skipping duplicate", command.sourceEventId());
            return false;
        }

        Notification notification = notificationPersistencePort.save(Notification.builder()
                .sourceEventId(command.sourceEventId())
                .userId(command.userId())
                .notificationType(command.type())
                .title(command.title())
                .message(command.message())
                .entityType(command.entityType())
                .entityId(command.entityId())
                .recipientEmail(command.recipientEmail())
                .createdAt(LocalDateTime.now())
                .build());

        // Canal por defecto INTERNAL cuando el productor no especifica nada.
        Set<ChannelName> requested = command.channels().isEmpty()
                ? Set.of(ChannelName.INTERNAL)
                : command.channels();

        for (Long channelId : resolveChannelIds(requested)) {
            notificationDeliveryServicePort.deliver(notification.getId(), channelId, command.protectedData());
        }
        return true;
    }

    /** Traduce los nombres de canal a sus ids reales leyendo el catálogo (única fuente de resolución). */
    private List<Long> resolveChannelIds(Set<ChannelName> channels) {
        Set<String> names = channels.stream().map(Enum::name).collect(Collectors.toSet());
        return notificationChannelServicePort.getChannels().stream()
                .filter(channel -> names.contains(channel.getName()))
                .map(channel -> channel.getId().longValue())
                .collect(Collectors.toList());
    }
}
