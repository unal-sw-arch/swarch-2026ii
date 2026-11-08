package com.pilosia.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class NotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private final NotificationStore store;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${notifications.queue:pilosia.notifications}")
    public void receive(String message) {
        log.info("Received notification: {}", message);
        try {
            store.add(objectMapper.readValue(message, new TypeReference<>() {}));
        } catch (Exception error) {
            log.error("Could not parse notification message", error);
        }
    }
}
