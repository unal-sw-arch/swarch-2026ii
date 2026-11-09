package com.pilosia.notifications;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotificationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }

    // courses-service publica en este exchange; cada servicio tiene su propia cola
    @Bean
    FanoutExchange eventsExchange() {
        return new FanoutExchange("pilosia.events", true, false);
    }

    @Bean
    Queue notificationsQueue() {
        return new Queue("pilosia.notifications", true);
    }

    @Bean
    Binding notificationsBinding(Queue notificationsQueue, FanoutExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange);
    }
}
