package com.parosurvivors.serviya.feedback.application.services;

import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackTagCatalogServicePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Catálogo de tags disponibles para reseñas de servicio (service_feedback_tags_catalog).
 * Solo lectura; no hay alta/edición de tags desde la aplicación (se gestionan por script/admin BD).
 */
@Component
@RequiredArgsConstructor
public class ServiceFeedbackTagCatalogService implements ServiceFeedbackTagCatalogServicePort {

    private final ServiceFeedbackTagCatalogPersistencePort serviceFeedbackTagCatalogPersistencePort;

    @Override
    public List<ServiceFeedbackTagCatalog> getCatalog() {
        return serviceFeedbackTagCatalogPersistencePort.findAll();
    }
}