package com.parosurvivors.serviya.metrics.application.ports.output;

import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceMetricsPersistencePort {
    ServiceMetrics save(ServiceMetrics metrics);
    ServiceMetrics update(ServiceMetrics metrics);
    Optional<ServiceMetrics> findByServiceId(Long serviceId);
    List<ServiceMetrics> findByServiceIdIn(Collection<Long> serviceIds);
}
