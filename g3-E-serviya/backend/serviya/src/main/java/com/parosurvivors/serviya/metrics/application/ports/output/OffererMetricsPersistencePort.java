package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.OffererMetrics;

import java.util.Optional;

public interface OffererMetricsPersistencePort {
    OffererMetrics save(OffererMetrics metrics);
    OffererMetrics update(OffererMetrics metrics);
    Optional<OffererMetrics> findByOffererId(Long offererId);
}
