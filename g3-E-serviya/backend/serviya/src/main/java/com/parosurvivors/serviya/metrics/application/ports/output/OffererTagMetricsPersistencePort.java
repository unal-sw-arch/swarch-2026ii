package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;

import java.util.List;
import java.util.Optional;

public interface OffererTagMetricsPersistencePort {
    OffererTagMetrics save(OffererTagMetrics metrics);
    OffererTagMetrics update(OffererTagMetrics metrics);
    List<OffererTagMetrics> findByOffererId(Long offererId);
    Optional<OffererTagMetrics> findByOffererIdAndTagId(Long offererId, Long tagId);
}
