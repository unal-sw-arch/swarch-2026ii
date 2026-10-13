package com.parosurvivors.serviya.feedback.application.ports.output;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;

import java.util.List;
import java.util.Optional;

public interface ServiceFeedbackTagCatalogPersistencePort {
    List<ServiceFeedbackTagCatalog> findAll();
    Optional<ServiceFeedbackTagCatalog> findById(Long id);
}
