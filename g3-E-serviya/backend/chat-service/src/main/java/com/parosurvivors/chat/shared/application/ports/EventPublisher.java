package com.parosurvivors.chat.shared.application.ports;

import java.util.Map;

/**
 * Publica un evento por el outbox (ADR-009): se escribe en la MISMA transacción que el cambio de negocio y un
 * relay lo envía a RabbitMQ. El adaptador arma el sobre común (event_id, occurred_at, correlation_id...).
 */
public interface EventPublisher {

    void publish(String exchange, String routingKey, Map<String, Object> data);
}
