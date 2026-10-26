package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ClientFeedbackTagCatalogPersistenceMapper;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ClientFeedbackTagCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientFeedbackTagCatalogPersistenceAdapter implements ClientFeedbackTagCatalogPersistencePort {

    private final ClientFeedbackTagCatalogRepository repository;
    private final ClientFeedbackTagCatalogPersistenceMapper mapper;

    @Override
    public List<ClientFeedbackTagCatalog> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ClientFeedbackTagCatalog> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
