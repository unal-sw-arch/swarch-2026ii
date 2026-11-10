package com.parosurvivors.serviya.feedback.api;

/**
 * Comandos publicos del modulo feedback.
 */
public interface FeedbackCommandApi {

    boolean revertServiceFeedback(Long feedbackId);

    boolean revertClientFeedback(Long feedbackId);
}
