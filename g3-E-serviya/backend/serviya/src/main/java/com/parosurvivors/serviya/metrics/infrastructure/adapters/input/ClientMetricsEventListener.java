package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.domain.RequestCreatedEvent;
import com.parosurvivors.serviya.shared.events.domain.RequestStatusChangedEvent;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada de las métricas del cliente. Escucha el feedback del oferente al cliente
 * (resuelve conteos de tags positivos/negativos) y los cambios de estado de sus solicitudes.
 * Delega en {@link ClientMetricsServicePort}. AFTER_COMMIT del publicador.
 */
@Component
@RequiredArgsConstructor
public class ClientMetricsEventListener {

    private final ClientMetricsServicePort clientMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ClientFeedbackSubmittedEvent event) {
        int positive = countPositive(event.tags());
        int negative = size(event.tags()) - positive;
        clientMetricsServicePort.applyClientFeedbackSubmitted(
                event.clientId(), event.rating(), event.hasComment(), positive, negative);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ClientFeedbackRevertedEvent event) {
        int positive = countPositive(event.tags());
        int negative = size(event.tags()) - positive;
        clientMetricsServicePort.applyClientFeedbackReverted(
                event.clientId(), event.rating(), event.hasComment(), positive, negative);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestStatusChanged(RequestStatusChangedEvent event) {
        clientMetricsServicePort.applyRequestStatusChanged(event.clientId(), event.newStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestCreated(RequestCreatedEvent event) {
        clientMetricsServicePort.incrementRequestsSent(event.clientId());
    }

    private int countPositive(List<TagRef> tags) {
        return tags == null ? 0 : (int) tags.stream().filter(TagRef::isPositive).count();
    }

    private int size(List<TagRef> tags) {
        return tags == null ? 0 : tags.size();
    }
}
