package com.parosurvivors.serviya.metrics.infrastructure.adapters.input;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientTagMetricsServicePort;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Adaptador de entrada que agrega, por cliente, los conteos de tags de las reseñas que recibió.
 * UPSERT/decremento en {@link ClientTagMetricsServicePort}. AFTER_COMMIT del publicador.
 */
@Component
@RequiredArgsConstructor
public class ClientTagMetricsEventListener {

    private final ClientTagMetricsServicePort clientTagMetricsServicePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackSubmitted(ClientFeedbackSubmittedEvent event) {
        clientTagMetricsServicePort.incrementTags(event.clientId(), tagIds(event.tags()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackReverted(ClientFeedbackRevertedEvent event) {
        clientTagMetricsServicePort.decrementTags(event.clientId(), tagIds(event.tags()));
    }

    private List<Long> tagIds(List<TagRef> tags) {
        return tags == null ? List.of() : tags.stream().map(TagRef::tagId).toList();
    }
}
