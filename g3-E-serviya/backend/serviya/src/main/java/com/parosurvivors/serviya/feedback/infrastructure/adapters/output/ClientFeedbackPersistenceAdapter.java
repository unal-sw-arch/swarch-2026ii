package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackEntity;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ClientFeedbackPersistenceMapper;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ClientFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientFeedbackPersistenceAdapter implements ClientFeedbackPersistencePort {

    private final ClientFeedbackRepository repository;
    private final ClientFeedbackPersistenceMapper mapper;

    @Override
    public ClientFeedback save(ClientFeedback feedback) {
        ClientFeedbackEntity saved = repository.save(mapper.toEntity(feedback));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ClientFeedback> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ClientFeedback> findByRequestId(Long requestId) {
        return repository.findByRequestId(requestId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByRequestId(Long requestId) {
        return repository.existsByRequestId(requestId);
    }

    @Override
    public List<ClientFeedback> findByClientId(Long clientId) {
        return repository.findByClientId(clientId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClientFeedback> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ClientFeedback> findByClientId(Long clientId, Pageable pageable) {
        return repository.findByClientId(clientId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ClientFeedback> findByOffererId(Long offererId, Pageable pageable) {
        return repository.findByOffererId(offererId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ClientFeedback> findByClientIdAndFilters(Long clientId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable) {
        return repository.findByClientIdAndFilters(clientId, keyword, ratingMin, ratingMax, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ClientFeedback> findByOffererIdAndFilters(Long offererId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable) {
        return repository.findByOffererIdAndFilters(offererId, keyword, ratingMin, ratingMax, pageable).map(mapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}