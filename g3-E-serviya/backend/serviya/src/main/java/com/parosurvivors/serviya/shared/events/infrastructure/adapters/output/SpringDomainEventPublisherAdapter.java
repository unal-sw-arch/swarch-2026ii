package com.parosurvivors.serviya.shared.events.infrastructure.adapters.output;

import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida que implementa {@link DomainEventPublisherPort} sobre el
 * {@code ApplicationEventPublisher} de Spring. Único punto donde la aplicación toca el
 * mecanismo de eventos: publicar aquí hace que los {@code @TransactionalEventListener}
 * de métricas se disparen tras confirmarse la transacción del publicador (AFTER_COMMIT).
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
