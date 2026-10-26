package com.parosurvivors.serviya.notifications.application.services;

import com.parosurvivors.serviya.notifications.application.dto.NotificationRequest;
import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.application.ports.output.NotificationOutboxPort;
import com.parosurvivors.serviya.notifications.application.ports.output.RecipientEmailPort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cliente de notifications-service. Arma el comando y lo deja en el outbox. No registra ni entrega nada.
 */
@Component
@RequiredArgsConstructor
public class NotificationService implements NotificationServicePort {

    private final NotificationOutboxPort notificationOutboxPort;
    private final RecipientEmailPort recipientEmailPort;

    @Override
    @Transactional // se une a la transacción del llamador: outbox y negocio confirman (o revierten) juntos
    public void notify(Long userId, String type, String title, String message, String entityType, Long entityId,
                       Set<ChannelName> channels, Map<String, String> protectedData) {
        Set<ChannelName> requested = channels == null ? Set.of() : Set.copyOf(channels);

        // Solo se resuelve (y viaja) el correo cuando se pidió EMAIL: minimización de PII en el bus.
        String recipientEmail = requested.contains(ChannelName.EMAIL)
                ? recipientEmailPort.findEmail(userId).orElse(null)
                : null;

        notificationOutboxPort.enqueue(new NotificationRequest(
                UUID.randomUUID().toString(),
                Instant.now(),
                userId,
                type,
                title,
                message,
                entityType,
                entityId,
                requested,
                recipientEmail,
                protectedData == null ? Map.of() : Map.copyOf(protectedData)));
    }
}
