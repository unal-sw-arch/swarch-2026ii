package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.ServiceTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceTagMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.ServiceTagMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.ServiceTagMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceTagMetricsPersistenceAdapter implements ServiceTagMetricsPersistencePort {

    private final ServiceTagMetricsRepository repository;
    private final ServiceTagMetricsPersistenceMapper mapper;

    @Override
    public ServiceTagMetrics save(ServiceTagMetrics metrics) {
        ServiceTagMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public ServiceTagMetrics update(ServiceTagMetrics metrics) {
        ServiceTagMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public List<ServiceTagMetrics> findByServiceId(Long serviceId) {
        return repository.findByServiceId(serviceId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ServiceTagMetrics> findByServiceIdAndTagId(Long serviceId, Long tagId) {
        return repository.findByServiceIdAndTagId(serviceId, tagId).map(mapper::toDomain);
    }
}
