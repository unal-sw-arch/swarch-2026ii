package com.parosurvivors.serviya.feedback.application.ports.output;

import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;

import java.util.List;
import java.util.Optional;

public interface ClientFeedbackTagCatalogPersistencePort {
    List<ClientFeedbackTagCatalog> findAll();
    Optional<ClientFeedbackTagCatalog> findById(Long id);
}
