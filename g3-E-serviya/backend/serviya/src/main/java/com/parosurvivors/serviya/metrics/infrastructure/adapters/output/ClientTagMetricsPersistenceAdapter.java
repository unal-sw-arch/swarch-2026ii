package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.ClientTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientTagMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.ClientTagMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.ClientTagMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientTagMetricsPersistenceAdapter implements ClientTagMetricsPersistencePort {

    private final ClientTagMetricsRepository repository;
    private final ClientTagMetricsPersistenceMapper mapper;

    @Override
    public ClientTagMetrics save(ClientTagMetrics metrics) {
        ClientTagMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public ClientTagMetrics update(ClientTagMetrics metrics) {
        ClientTagMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public List<ClientTagMetrics> findByClientId(Long clientId) {
        return repository.findByClientId(clientId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ClientTagMetrics> findByClientIdAndTagId(Long clientId, Long tagId) {
        return repository.findByClientIdAndTagId(clientId, tagId).map(mapper::toDomain);
    }
}
