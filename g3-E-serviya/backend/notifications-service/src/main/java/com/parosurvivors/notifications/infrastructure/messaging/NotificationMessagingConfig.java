package com.parosurvivors.notifications.infrastructure.messaging;

import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Topología RabbitMQ del lado CONSUMIDOR. El exchange lo declaran ambos lados (idempotente); la cola, su
 * binding y la dead-letter son del servicio, que es su dueño.
 *
 * <pre>
 *  monolito ──(outbox relay)──▶ [serviya.notifications] (topic) ──notification.requested.v1──▶
 *      [notifications-service.notification-requested] ──(tras agotar reintentos)──▶
 *      [serviya.notifications.dlx] ──▶ [notifications-service.notification-requested.dlq]
 * </pre>
 */
@Configuration
public class NotificationMessagingConfig {

    public static final String EXCHANGE = "serviya.notifications";
    public static final String ROUTING_KEY = "notification.requested.v1";
    public static final String QUEUE = "notifications-service.notification-requested";
    public static final String DEAD_LETTER_EXCHANGE = "serviya.notifications.dlx";
    public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

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

    /**
     * Reintentos del listener: los configura {@code spring.rabbitmq.listener.simple.retry.*}, pero por defecto
     * reintentan CUALQUIER excepción. Un mensaje permanentemente inválido
     * ({@link AmqpRejectAndDontRequeueException}) va directo a la DLQ sin gastar reintentos.
     */
    @Bean
    RabbitRetryTemplateCustomizer notificationListenerRetryPolicy(RabbitProperties rabbitProperties) {
        return (target, template) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                int maxAttempts = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
                template.setRetryPolicy(new SimpleRetryPolicy(maxAttempts,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true));
            }
        };
    }
}
