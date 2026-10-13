package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.DomainEventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Implementa {@link DomainEventPublisherPort} con el {@link ApplicationEventPublisher} de Spring (in-process). */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
