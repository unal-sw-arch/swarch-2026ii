package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.OffererTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererTagMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.OffererTagMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.OffererTagMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OffererTagMetricsPersistenceAdapter implements OffererTagMetricsPersistencePort {

    private final OffererTagMetricsRepository repository;
    private final OffererTagMetricsPersistenceMapper mapper;

    @Override
    public OffererTagMetrics save(OffererTagMetrics metrics) {
        OffererTagMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public OffererTagMetrics update(OffererTagMetrics metrics) {
        OffererTagMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public List<OffererTagMetrics> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OffererTagMetrics> findByOffererIdAndTagId(Long offererId, Long tagId) {
        return repository.findByOffererIdAndTagId(offererId, tagId).map(mapper::toDomain);
    }
}
