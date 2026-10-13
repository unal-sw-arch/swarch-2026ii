package com.parosurvivors.serviya.feedback.application.dto;

/**
 * Placeholder de objeto de traspaso interno (no expuesto por endpoint).
 * Tras unificar rating + reseña en una sola entidad Feedback, las fachadas
 * (Service/Client FeedbackService) ya no ensamblan sub-servicios de rating/reseña:
 * pasan a FeedbackFlow sus colaboradores de persistencia (feedback + tags) side-specific
 * para el flujo común de creación/borrado.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 5 - FeedbackFlow).
 * TODO: definir referencias a los puertos de persistencia de feedback y de tags.
 */
public class FeedbackParts {
}
