package com.parosurvivors.serviya.feedback.application.ports.input;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;

import java.util.List;

/**
 * Puerto de entrada de ServiceFeedbackTagCatalogService.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 5).
 */
public interface ServiceFeedbackTagCatalogServicePort {

    List<ServiceFeedbackTagCatalog> getCatalog();
}
