package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.ServiceMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Métricas precalculadas de un servicio. La consulta es pública (RF-040); la escritura la disparan
 * los eventos de feedback (via {@code ServiceMetricsEventListener}). Cada apply* corre en su propia
 * transacción (se invoca AFTER_COMMIT del publicador). Patrón find-or-create: si aún no existe la
 * fila 1-a-1 del servicio, se crea al primer feedback.
 */
@Component
@RequiredArgsConstructor
public class ServiceMetricsService implements ServiceMetricsServicePort {

    private final ServiceMetricsPersistencePort serviceMetricsPersistencePort;

    @Override
    public ServiceMetrics getMetrics(Long serviceId) {
        return serviceMetricsPersistencePort.findByServiceId(serviceId)
                .orElse(ServiceMetrics.builder().serviceId(serviceId).build());
    }

    @Override
    public Map<Long, ServiceMetrics> getMetricsByServiceIds(Collection<Long> serviceIds) {
        List<ServiceMetrics> metricsList = serviceMetricsPersistencePort.findByServiceIdIn(serviceIds);
        return metricsList.stream()
                .collect(Collectors.toMap(ServiceMetrics::getServiceId, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyFeedbackSubmitted(Long serviceId, Integer rating, boolean hasComment) {
        ServiceMetrics metrics = findOrCreate(serviceId);
        if (rating != null) {
            metrics.registerRating(rating);
        }
        if (hasComment) {
            metrics.incrementComments();
        }
        persist(metrics);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyFeedbackReverted(Long serviceId, Integer rating, boolean hasComment) {
        serviceMetricsPersistencePort.findByServiceId(serviceId).ifPresent(metrics -> {
            if (rating != null) {
                metrics.removeRating(rating);
            }
            if (hasComment) {
                metrics.decrementComments();
            }
            serviceMetricsPersistencePort.update(metrics);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRequestsReceived(Long serviceId) {
        ServiceMetrics metrics = findOrCreate(serviceId);
        metrics.incrementRequestsReceived();
        persist(metrics);
    }

    private ServiceMetrics findOrCreate(Long serviceId) {
        return serviceMetricsPersistencePort.findByServiceId(serviceId)
                .orElseGet(() -> ServiceMetrics.builder().serviceId(serviceId).build());
    }

    private void persist(ServiceMetrics metrics) {
        if (metrics.getId() == null) {
            serviceMetricsPersistencePort.save(metrics);
        } else {
            serviceMetricsPersistencePort.update(metrics);
        }
    }
}
