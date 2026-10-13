package com.parosurvivors.notifications.application.ports.output;

/**
 * Puerto de salida para publicar eventos internos del servicio sin que la capa de aplicación conozca el
 * mecanismo concreto (Spring {@code ApplicationEventPublisher}).
 */
public interface DomainEventPublisherPort {

    void publish(Object event);
}
