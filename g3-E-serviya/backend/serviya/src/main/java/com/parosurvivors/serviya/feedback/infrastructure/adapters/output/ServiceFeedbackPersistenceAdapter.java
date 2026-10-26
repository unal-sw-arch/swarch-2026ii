package com.parosurvivors.serviya.feedback.infrastructure.adapters.output;

import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackEntity;
import com.parosurvivors.serviya.feedback.infrastructure.mappers.ServiceFeedbackPersistenceMapper;
import com.parosurvivors.serviya.feedback.infrastructure.repositories.ServiceFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceFeedbackPersistenceAdapter implements ServiceFeedbackPersistencePort {

    private final ServiceFeedbackRepository repository;
    private final ServiceFeedbackPersistenceMapper mapper;

    @Override
    public ServiceFeedback save(ServiceFeedback feedback) {
        ServiceFeedbackEntity saved = repository.save(mapper.toEntity(feedback));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ServiceFeedback> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ServiceFeedback> findByRequestId(Long requestId) {
        return repository.findByRequestId(requestId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByRequestId(Long requestId) {
        return repository.existsByRequestId(requestId);
    }

    @Override
    public List<ServiceFeedback> findByClientId(Long clientId) {
        return repository.findByClientId(clientId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceFeedback> findRecentByServiceId(Long serviceId, int limit) {
        return repository.findByServiceIdAndCommentIsNotNullOrderByCreatedAtDesc(serviceId, PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ServiceFeedback> findByServiceId(Long serviceId, Pageable pageable) {
        return repository.findByServiceId(serviceId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ServiceFeedback> findByClientId(Long clientId, Pageable pageable) {
        return repository.findByClientId(clientId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ServiceFeedback> findByServiceIdAndFilters(Long serviceId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable) {
        return repository.findByServiceIdAndFilters(serviceId, keyword, ratingMin, ratingMax, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ServiceFeedback> findByClientIdAndFilters(Long clientId, String keyword, Integer ratingMin, Integer ratingMax, Pageable pageable) {
        return repository.findByClientIdAndFilters(clientId, keyword, ratingMin, ratingMax, pageable).map(mapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}