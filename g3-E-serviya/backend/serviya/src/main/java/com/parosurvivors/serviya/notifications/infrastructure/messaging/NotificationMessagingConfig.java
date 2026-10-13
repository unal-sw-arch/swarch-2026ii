package com.parosurvivors.serviya.notifications.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ del lado PRODUCTOR. Los nombres son contrato con notifications-service (que declara lo
 * mismo con los mismos argumentos; las declaraciones son idempotentes).
 *
 * <p>Se declara también la COLA del consumidor, no solo el exchange: si el monolito publica antes de que
 * notifications-service haya arrancado por primera vez, un exchange sin cola enlazada DESCARTARÍA el mensaje
 * en silencio. Con la cola declarada aquí, los mensajes esperan al consumidor.</p>
 */
@Configuration
public class NotificationMessagingConfig {

    public static final String EXCHANGE = "serviya.notifications";
    public static final String ROUTING_KEY = "notification.requested.v1";
    static final String QUEUE = "notifications-service.notification-requested";
    static final String DEAD_LETTER_EXCHANGE = "serviya.notifications.dlx";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    @Bean
    TopicExchange notificationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationRequestedQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue notificationRequestedDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationRequestedBinding() {
        return BindingBuilder.bind(notificationRequestedQueue()).to(notificationsExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding notificationRequestedDeadLetterBinding() {
        return BindingBuilder.bind(notificationRequestedDeadLetterQueue())
                .to(notificationsDeadLetterExchange()).with(DEAD_LETTER_QUEUE);
    }
}
