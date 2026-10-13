package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;

import java.util.List;
import java.util.Optional;

public interface ClientTagMetricsPersistencePort {
    ClientTagMetrics save(ClientTagMetrics metrics);
    ClientTagMetrics update(ClientTagMetrics metrics);
    List<ClientTagMetrics> findByClientId(Long clientId);
    Optional<ClientTagMetrics> findByClientIdAndTagId(Long clientId, Long tagId);
}
