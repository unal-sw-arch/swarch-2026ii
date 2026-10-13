package com.parosurvivors.serviya.feedback.application.services;

import com.parosurvivors.serviya.feedback.application.dto.FeedbackParts;
import com.parosurvivors.serviya.feedback.application.ports.input.FeedbackFlowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementacion placeholder de FeedbackFlowPort.
 * Metodos sin logica aun (lanzan UnsupportedOperationException); dependencias inyectadas.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class FeedbackFlow implements FeedbackFlowPort {

    @Override
    public void submit(FeedbackParts parts, Long requestId, Integer rating, String comment, List<Long> tagIds) {
        throw new UnsupportedOperationException("TODO: submit — placeholder, ver estructura-servicios.docx");
    }

    @Override
    public void remove(FeedbackParts parts, Long requestId) {
        throw new UnsupportedOperationException("TODO: remove — placeholder, ver estructura-servicios.docx");
    }
}
