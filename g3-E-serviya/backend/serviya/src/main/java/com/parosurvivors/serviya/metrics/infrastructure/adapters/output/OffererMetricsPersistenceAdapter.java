package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.OffererMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.OffererMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.OffererMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OffererMetricsPersistenceAdapter implements OffererMetricsPersistencePort {

    private final OffererMetricsRepository repository;
    private final OffererMetricsPersistenceMapper mapper;

    @Override
    public OffererMetrics save(OffererMetrics metrics) {
        OffererMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public OffererMetrics update(OffererMetrics metrics) {
        OffererMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<OffererMetrics> findByOffererId(Long offererId) {
        return repository.findByOffererId(offererId).map(mapper::toDomain);
    }
}
