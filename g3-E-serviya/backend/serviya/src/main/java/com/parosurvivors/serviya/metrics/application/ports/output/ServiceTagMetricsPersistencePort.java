package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;

import java.util.List;
import java.util.Optional;

public interface ServiceTagMetricsPersistencePort {
    ServiceTagMetrics save(ServiceTagMetrics metrics);
    ServiceTagMetrics update(ServiceTagMetrics metrics);
    List<ServiceTagMetrics> findByServiceId(Long serviceId);
    Optional<ServiceTagMetrics> findByServiceIdAndTagId(Long serviceId, Long tagId);
}
