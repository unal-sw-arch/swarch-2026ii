package com.parosurvivors.serviya.feedback.application.ports.input;

import com.parosurvivors.serviya.feedback.application.dto.FeedbackParts;

import java.util.List;

/**
 * Puerto de entrada de FeedbackFlow — flujo común de feedback (creación y borrado),
 * side-agnostic, usado por las fachadas Service/Client FeedbackService. La parte de resena
 * (comment + tagIds) viaja desestructurada; no usa tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 5).
 */
public interface FeedbackFlowPort {

    void submit(FeedbackParts parts, Long requestId, Integer rating, String comment, List<Long> tagIds);

    void remove(FeedbackParts parts, Long requestId);
}
