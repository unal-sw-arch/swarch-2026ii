package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ServiceFeedbackTagCatalogPersistenceMapper;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ServiceFeedbackTagCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceFeedbackTagCatalogPersistenceAdapter implements ServiceFeedbackTagCatalogPersistencePort {

    private final ServiceFeedbackTagCatalogRepository repository;
    private final ServiceFeedbackTagCatalogPersistenceMapper mapper;

    @Override
    public List<ServiceFeedbackTagCatalog> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ServiceFeedbackTagCatalog> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
