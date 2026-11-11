package com.kanasta.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

// Sends email events to the message broker. The Notifications Service sends the emails later.
@Component
public class NotificationPublisher {

    public static final String QUEUE = "notifications";

    private final RabbitTemplate rabbit;
    private final ObjectMapper json = new ObjectMapper();

    public NotificationPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(String type, User user) {
        try {
            Map<String, Object> event = Map.of("type", type, "to", user.email(), "name", user.name(), "data", Map.of());
            rabbit.convertAndSend(QUEUE, json.writeValueAsString(event));
        } catch (Exception e) {
            // The account action must not fail if the broker is down
            System.err.println("Could not publish " + type + ": " + e.getMessage());
        }
    }
}
