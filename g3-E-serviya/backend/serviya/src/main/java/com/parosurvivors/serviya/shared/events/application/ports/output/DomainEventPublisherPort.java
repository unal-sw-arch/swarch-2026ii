package com.parosurvivors.serviya.shared.events.application.ports.output;

/**
 * Puerto de salida (hexagonal) para publicar eventos de dominio sin que la capa de aplicación
 * conozca el mecanismo concreto (Spring {@code ApplicationEventPublisher}). Los servicios de
 * aplicación que publican (feedback, requests) dependen de esta interfaz; el adaptador
 * {@link SpringDomainEventPublisherAdapter} la implementa en infraestructura.
 *
 * <p>Contrato cross-módulo compartido: los eventos viajan como records autocontenidos de
 * {@code shared.events}, de modo que los listeners de métricas actualicen sus totales sin
 * volver a consultar la BD.</p>
 */
public interface DomainEventPublisherPort {

    /** Publica un evento de dominio. La entrega a los listeners la resuelve el adaptador. */
    void publish(Object event);
}
