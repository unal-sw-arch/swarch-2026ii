package com.parosurvivors.notifications.infrastructure.adapters.input;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.notifications.application.dto.command.NotifyCommand;
import com.parosurvivors.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.ChannelName;
import com.parosurvivors.notifications.infrastructure.messaging.NotificationMessagingConfig;
import com.parosurvivors.notifications.infrastructure.messaging.NotificationRequestedMessage;
import com.parosurvivors.notifications.infrastructure.messaging.NotificationRequestedMessage.SealedContent;
import com.parosurvivors.notifications.infrastructure.messaging.PayloadSealer;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada (driving) desde el bus: traduce el mensaje {@code notification.requested.v1} a un
 * {@link NotifyCommand} y delega en el puerto. Es el sustituto remoto de la antigua llamada en memoria
 * {@code notificationServicePort.notify(...)} que hacían users/requests/reports/admin.
 *
 * <ul>
 *   <li><b>Mensaje inválido</b> (JSON roto, versión desconocida, sello que no abre): se rechaza SIN reencolar
 *       → va directo a la dead-letter queue (reintentar no lo arreglaría).</li>
 *   <li><b>Fallo transitorio</b> (BD caída…): la excepción se propaga y el contenedor reintenta con backoff
 *       ({@code spring.rabbitmq.listener.simple.retry.*}); agotados los intentos, a la DLQ.</li>
 *   <li><b>Duplicado</b> (entrega ≥1): idempotente por {@code eventId}, se confirma sin efectos.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NotificationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

    private final NotificationServicePort notificationServicePort;
    private final NotificationPersistencePort notificationPersistencePort;
    private final PayloadSealer payloadSealer;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = NotificationMessagingConfig.QUEUE)
    public void onNotificationRequested(Message amqpMessage) {
        NotifyCommand command = toCommand(amqpMessage.getBody());
        try {
            notificationServicePort.notify(command);
        } catch (DataIntegrityViolationException ex) {
            // Carrera entre dos copias del mismo evento: la UNIQUE de source_event_id rechazó la segunda.
            if (notificationPersistencePort.existsBySourceEventId(command.sourceEventId())) {
                log.info("Notification event {} processed concurrently by another consumer — duplicate ignored",
                        command.sourceEventId());
                return;
            }
            throw ex;
        }
    }

    /** Valida y traduce el mensaje. Cualquier error aquí es PERMANENTE → DLQ sin reintentos. */
    NotifyCommand toCommand(byte[] body) {
        NotificationRequestedMessage message;
        try {
            message = objectMapper.readValue(body, NotificationRequestedMessage.class);
        } catch (IOException ex) {
            throw new AmqpRejectAndDontRequeueException("Malformed notification.requested message", ex);
        }
        if (message.schemaVersion() != NotificationRequestedMessage.SCHEMA_VERSION) {
            throw new AmqpRejectAndDontRequeueException(
                    "Unsupported notification.requested schemaVersion " + message.schemaVersion());
        }
        if (message.eventId() == null || message.userId() == null || message.type() == null
                || message.title() == null || message.message() == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "notification.requested " + message.eventId() + " is missing required fields");
        }

        SealedContent sealed = openSealed(message);
        return new NotifyCommand(
                message.eventId(),
                message.userId(),
                message.type(),
                message.title(),
                message.message(),
                message.entityType(),
                message.entityId(),
                toChannels(message),
                sealed.recipientEmail(),
                sealed.protectedData());
    }

    private SealedContent openSealed(NotificationRequestedMessage message) {
        if (message.sealed() == null || message.sealed().isBlank()) {
            return new SealedContent(null, Map.of());
        }
        try {
            return objectMapper.readValue(payloadSealer.open(message.sealed()), SealedContent.class);
        } catch (IOException | IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException(
                    "Sealed content of notification.requested " + message.eventId() + " could not be opened "
                            + "(¿NOTIFICATIONS_PAYLOAD_KEY distinta entre monolito y servicio?)", ex);
        }
    }

    private Set<ChannelName> toChannels(NotificationRequestedMessage message) {
        if (message.channels() == null) {
            return Set.of();
        }
        Set<ChannelName> channels = EnumSet.noneOf(ChannelName.class);
        for (String name : message.channels()) {
            try {
                channels.add(ChannelName.valueOf(name));
            } catch (IllegalArgumentException ex) {
                // Un canal que este servicio aún no conoce no invalida el resto del mensaje.
                log.warn("Unknown channel '{}' in notification.requested {} — ignored", name, message.eventId());
            }
        }
        return channels;
    }
}
