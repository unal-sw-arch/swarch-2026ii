package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada que traduce los eventos de feedback de servicio a UPSERT/decremento de
 * conteos por tag en {@link ServiceTagMetricsServicePort}. Solo necesita los ids de tag del evento
 * (el conteo por tag no distingue sentimiento). AFTER_COMMIT del publicador.
 */
@Component
@RequiredArgsConstructor
public class ServiceTagMetricsEventListener {

    private final ServiceTagMetricsServicePort serviceTagMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ServiceFeedbackSubmittedEvent event) {
        serviceTagMetricsServicePort.incrementTags(event.serviceId(), tagIds(event.tags()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ServiceFeedbackRevertedEvent event) {
        serviceTagMetricsServicePort.decrementTags(event.serviceId(), tagIds(event.tags()));
    }

    private List<Long> tagIds(List<TagRef> tags) {
        return tags == null ? List.of() : tags.stream().map(TagRef::tagId).toList();
    }
}
