package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.OffererTagMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada que agrega, por oferente, los conteos de tags de las reseñas de sus
 * servicios. UPSERT/decremento en {@link OffererTagMetricsServicePort}. AFTER_COMMIT del publicador.
 */
@Component
@RequiredArgsConstructor
public class OffererTagMetricsEventListener {

    private final OffererTagMetricsServicePort offererTagMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ServiceFeedbackSubmittedEvent event) {
        offererTagMetricsServicePort.incrementTags(event.offererId(), tagIds(event.tags()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ServiceFeedbackRevertedEvent event) {
        offererTagMetricsServicePort.decrementTags(event.offererId(), tagIds(event.tags()));
    }

    private List<Long> tagIds(List<TagRef> tags) {
        return tags == null ? List.of() : tags.stream().map(TagRef::tagId).toList();
    }
}
