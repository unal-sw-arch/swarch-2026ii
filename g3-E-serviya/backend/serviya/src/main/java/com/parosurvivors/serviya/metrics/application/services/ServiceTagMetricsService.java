package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.ServiceTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conteo de tags recibidas por servicio. La escritura la disparan los eventos de feedback de
 * servicio (via {@code ServiceTagMetricsEventListener}) con patrón UPSERT: por cada tag, si existe
 * el par (serviceId, tagId) se ajusta el conteo, si no se crea en 1.
 */
@Component
@RequiredArgsConstructor
public class ServiceTagMetricsService implements ServiceTagMetricsServicePort {

    private final ServiceTagMetricsPersistencePort serviceTagMetricsPersistencePort;

    @Override
    public List<ServiceTagMetrics> getTagMetrics(Long serviceId) {
        return serviceTagMetricsPersistencePort.findByServiceId(serviceId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementTags(Long serviceId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            serviceTagMetricsPersistencePort.findByServiceIdAndTagId(serviceId, tagId).ifPresentOrElse(
                    existing -> {
                        existing.increment();
                        serviceTagMetricsPersistencePort.update(existing);
                    },
                    () -> serviceTagMetricsPersistencePort.save(ServiceTagMetrics.builder()
                            .serviceId(serviceId)
                            .tagId(tagId)
                            .tagCount(1)
                            .build()));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrementTags(Long serviceId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            serviceTagMetricsPersistencePort.findByServiceIdAndTagId(serviceId, tagId).ifPresent(existing -> {
                existing.decrement();
                serviceTagMetricsPersistencePort.update(existing);
            });
        }
    }
}
