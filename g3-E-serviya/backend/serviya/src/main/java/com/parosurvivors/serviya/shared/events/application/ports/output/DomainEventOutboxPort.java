package com.parosurvivors.serviya.shared.events.application.ports.output;

import java.util.Map;

/**
 * Outbox transaccional para eventos de dominio que deben salir del monolito hacia RabbitMQ.
 *
 * <p>El evento se guarda en la misma transaccion de negocio; un relay lo publica cuando el broker
 * confirma. Si RabbitMQ cae, la operacion de negocio no se bloquea: los eventos quedan pendientes.</p>
 */
public interface DomainEventOutboxPort {

    void enqueue(String exchange, String routingKey, String eventType, String subject, Map<String, Object> data);
}
