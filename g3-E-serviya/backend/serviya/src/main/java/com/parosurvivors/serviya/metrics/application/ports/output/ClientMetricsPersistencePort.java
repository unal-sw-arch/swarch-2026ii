package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.ClientMetrics;

import java.util.Optional;

public interface ClientMetricsPersistencePort {
    ClientMetrics save(ClientMetrics metrics);
    ClientMetrics update(ClientMetrics metrics);
    Optional<ClientMetrics> findByClientId(Long clientId);
}
