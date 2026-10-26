package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.RequestCreatedEvent;
import com.parosurvivors.serviya.shared.events.domain.RequestStatusChangedEvent;
import com.parosurvivors.serviya.shared.events.domain.RescheduleProposalCreatedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada de las métricas del oferente. Escucha el feedback de servicio (resuelve los
 * conteos de tags positivos/negativos a partir del sentimiento que trae el evento) y los cambios de
 * estado de solicitud. Traduce a primitivos y delega en {@link OffererMetricsServicePort}.
 */
@Component
@RequiredArgsConstructor
public class OffererMetricsEventListener {

    private final OffererMetricsServicePort offererMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ServiceFeedbackSubmittedEvent event) {
        int positive = countPositive(event.tags());
        int negative = size(event.tags()) - positive;
        offererMetricsServicePort.applyServiceFeedbackSubmitted(
                event.offererId(), event.rating(), event.hasComment(), positive, negative);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ServiceFeedbackRevertedEvent event) {
        int positive = countPositive(event.tags());
        int negative = size(event.tags()) - positive;
        offererMetricsServicePort.applyServiceFeedbackReverted(
                event.offererId(), event.rating(), event.hasComment(), positive, negative);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestStatusChanged(RequestStatusChangedEvent event) {
        offererMetricsServicePort.applyRequestStatusChanged(event.offererId(), event.newStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestCreated(RequestCreatedEvent event) {
        offererMetricsServicePort.incrementRequestsReceived(event.offererId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduleProposalCreated(RescheduleProposalCreatedEvent event) {
        offererMetricsServicePort.incrementRescheduleProposalsSent(event.offererId());
    }

    private int countPositive(List<TagRef> tags) {
        return tags == null ? 0 : (int) tags.stream().filter(TagRef::isPositive).count();
    }

    private int size(List<TagRef> tags) {
        return tags == null ? 0 : tags.size();
    }
}
