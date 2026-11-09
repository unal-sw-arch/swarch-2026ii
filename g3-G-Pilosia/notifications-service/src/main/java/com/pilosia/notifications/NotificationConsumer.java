package com.pilosia.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

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
            Map<String, String> event = objectMapper.readValue(message, new TypeReference<>() {});
            // a los docentes solo les interesa saber cuándo alguien se inscribe en su grupo
            if ("EnrollmentCreated".equals(event.get("type"))) {
                store.add(event);
            }
        } catch (Exception error) {
            log.error("Could not parse notification message", error);
        }
    }
}
