package com.parosurvivors.serviya.feedback.application.ports.input;

import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;

import java.util.List;

/**
 * Puerto de entrada de ClientFeedbackTagCatalogService.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 5).
 */
public interface ClientFeedbackTagCatalogServicePort {

    List<ClientFeedbackTagCatalog> getCatalog();
}
