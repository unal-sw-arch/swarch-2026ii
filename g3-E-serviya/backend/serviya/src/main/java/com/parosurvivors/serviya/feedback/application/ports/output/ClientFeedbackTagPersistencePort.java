package com.parosurvivors.serviya.feedback.application.ports.output;

import java.util.List;

/**
 * Puerto de salida para la tabla puente {@code client_feedback_tags}. Maneja los enlaces
 * feedback-etiqueta con identificadores planos; el feedback ({@code ClientFeedback}) y sus
 * {@code tagIds} se componen en el servicio de aplicación.
 */
public interface ClientFeedbackTagPersistencePort {
    void addTags(Long feedbackId, List<Long> tagIds);
    List<Long> findTagIdsByFeedbackId(Long feedbackId);
    void deleteByFeedbackId(Long feedbackId);
}
