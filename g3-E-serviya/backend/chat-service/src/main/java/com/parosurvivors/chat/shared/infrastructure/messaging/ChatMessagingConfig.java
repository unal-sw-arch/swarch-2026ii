package com.parosurvivors.chat.shared.infrastructure.messaging;

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
 * Topología RabbitMQ del chat (documents/microservicios/chat/decisiones-infraestructura-implementacion.md §3).
 * Infraestructura ({@code infra/rabbitmq/setup-topology.sh}) declara los exchanges y el usuario {@code chat}; el
 * chat declara SUS colas, bindings y DLQ. Con los permisos del usuario {@code chat} solo puede (re)declarar
 * {@code serviya.chat} y {@code serviya.chat.dlx}: los exchanges ajenos se referencian por nombre, sin declararlos.
 *
 * <pre>
 *  serviya.requests ──requests.service_request.*──▶ [chat-service.requests] ──▶ DLX ──▶ [...requests.dlq]
 *  serviya.users    ──users.user.*─────────────────▶ [chat-service.users]    ──▶ DLX ──▶ [...users.dlq]
 *  chat (outbox relay) ──▶ serviya.chat (chat.*)  y  serviya.notifications (notification.requested.v1)
 * </pre>
 */
@Configuration
public class ChatMessagingConfig {

    public static final String REQUESTS_EXCHANGE = "serviya.requests";
    public static final String USERS_EXCHANGE = "serviya.users";
    public static final String CHAT_EXCHANGE = "serviya.chat";
    public static final String NOTIFICATIONS_EXCHANGE = "serviya.notifications";
    public static final String DEAD_LETTER_EXCHANGE = "serviya.chat.dlx";

    public static final String REQUESTS_QUEUE = "chat-service.requests";
    public static final String USERS_QUEUE = "chat-service.users";

    public static final String REQUEST_CREATED = "requests.service_request.created";
    public static final String REQUEST_STATUS_CHANGED = "requests.service_request.status_changed";
    public static final String USER_REGISTERED = "users.user.registered";
    public static final String USER_UPDATED = "users.user.updated";
    public static final String USER_SUSPENDED = "users.user.suspended";
    public static final String USER_REACTIVATED = "users.user.reactivated";
    public static final String USER_DELETED = "users.user.deleted";

    @Bean TopicExchange chatExchange() { return new TopicExchange(CHAT_EXCHANGE, true, false); }

    @Bean DirectExchange chatDeadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean
    Queue requestsQueue() {
        return QueueBuilder.durable(REQUESTS_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(REQUESTS_QUEUE + ".dlq").build();
    }

    @Bean
    Queue requestsDeadLetterQueue() {
        return QueueBuilder.durable(REQUESTS_QUEUE + ".dlq").build();
    }

    @Bean
    Queue usersQueue() {
        return QueueBuilder.durable(USERS_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(USERS_QUEUE + ".dlq").build();
    }

    @Bean
    Queue usersDeadLetterQueue() {
        return QueueBuilder.durable(USERS_QUEUE + ".dlq").build();
    }

    /** Comodín acordado (§3.3): los tipos que el chat no usa llegan y se ignoran sin ir a la DLQ. */
    @Bean
    Binding requestsBinding() {
        return new Binding(REQUESTS_QUEUE, Binding.DestinationType.QUEUE, REQUESTS_EXCHANGE, "requests.service_request.*",
                null);
    }

    @Bean
    Binding usersBinding() {
        return new Binding(USERS_QUEUE, Binding.DestinationType.QUEUE, USERS_EXCHANGE, "users.user.*", null);
    }

    @Bean
    Binding requestsDeadLetterBinding() {
        return BindingBuilder.bind(requestsDeadLetterQueue()).to(chatDeadLetterExchange())
                .with(REQUESTS_QUEUE + ".dlq");
    }

    @Bean
    Binding usersDeadLetterBinding() {
        return BindingBuilder.bind(usersDeadLetterQueue()).to(chatDeadLetterExchange()).with(USERS_QUEUE + ".dlq");
    }

    /**
     * Reintentos del listener (spring.rabbitmq.listener.simple.retry.*): por defecto reintentan CUALQUIER
     * excepción. Un mensaje permanentemente inválido ({@link AmqpRejectAndDontRequeueException}) va directo
     * a la DLQ sin gastar reintentos.
     */
    @Bean
    RabbitRetryTemplateCustomizer chatListenerRetryPolicy(RabbitProperties rabbitProperties) {
        return (target, template) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                int maxAttempts = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
                template.setRetryPolicy(new SimpleRetryPolicy(maxAttempts,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true));
            }
        };
    }
}
