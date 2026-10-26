package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.ServiceMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.ServiceMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.ServiceMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceMetricsPersistenceAdapter implements ServiceMetricsPersistencePort {

    private final ServiceMetricsRepository repository;
    private final ServiceMetricsPersistenceMapper mapper;

    @Override
    public ServiceMetrics save(ServiceMetrics metrics) {
        ServiceMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public ServiceMetrics update(ServiceMetrics metrics) {
        ServiceMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<ServiceMetrics> findByServiceId(Long serviceId) {
        return repository.findByServiceId(serviceId).map(mapper::toDomain);
    }

    @Override
    public List<ServiceMetrics> findByServiceIdIn(Collection<Long> serviceIds) {
        return repository.findByServiceIdIn(serviceIds).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
