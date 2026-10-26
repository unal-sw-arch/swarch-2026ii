package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.RequestCreatedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada (driving) que traduce los eventos de feedback de servicio a llamadas del
 * puerto {@link ServiceMetricsServicePort}. Fino: no contiene lógica de métricas, solo mapea el
 * evento a primitivos y delega. Se dispara AFTER_COMMIT de la transacción del publicador (el
 * recalculo de métricas no revierte el feedback si falla).
 */
@Component
@RequiredArgsConstructor
public class ServiceMetricsEventListener {

    private final ServiceMetricsServicePort serviceMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ServiceFeedbackSubmittedEvent event) {
        serviceMetricsServicePort.applyFeedbackSubmitted(event.serviceId(), event.rating(), event.hasComment());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ServiceFeedbackRevertedEvent event) {
        serviceMetricsServicePort.applyFeedbackReverted(event.serviceId(), event.rating(), event.hasComment());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestCreated(RequestCreatedEvent event) {
        serviceMetricsServicePort.incrementRequestsReceived(event.serviceId());
    }
}
