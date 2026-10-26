package com.parosurvivors.serviya.metrics.infrastructure.adapters.output;

import com.parosurvivors.serviya.metrics.application.ports.output.ClientMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientMetricsEntity;
import com.parosurvivors.serviya.metrics.infrastructure.mappers.ClientMetricsPersistenceMapper;
import com.parosurvivors.serviya.metrics.infrastructure.repositories.ClientMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientMetricsPersistenceAdapter implements ClientMetricsPersistencePort {

    private final ClientMetricsRepository repository;
    private final ClientMetricsPersistenceMapper mapper;

    @Override
    public ClientMetrics save(ClientMetrics metrics) {
        ClientMetricsEntity saved = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(saved);
    }

    @Override
    public ClientMetrics update(ClientMetrics metrics) {
        ClientMetricsEntity updated = repository.save(mapper.toEntity(metrics));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<ClientMetrics> findByClientId(Long clientId) {
        return repository.findByClientId(clientId).map(mapper::toDomain);
    }
}
