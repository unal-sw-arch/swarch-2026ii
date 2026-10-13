package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.OffererTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.OffererTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conteo de tags recibidas por el oferente (agregado de todas las reseñas de sus servicios).
 * UPSERT por tag disparado por los eventos de feedback de servicio.
 */
@Component
@RequiredArgsConstructor
public class OffererTagMetricsService implements OffererTagMetricsServicePort {

    private final OffererTagMetricsPersistencePort offererTagMetricsPersistencePort;

    @Override
    public List<OffererTagMetrics> getTagMetrics(Long offererId) {
        return offererTagMetricsPersistencePort.findByOffererId(offererId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementTags(Long offererId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            offererTagMetricsPersistencePort.findByOffererIdAndTagId(offererId, tagId).ifPresentOrElse(
                    existing -> {
                        existing.increment();
                        offererTagMetricsPersistencePort.update(existing);
                    },
                    () -> offererTagMetricsPersistencePort.save(OffererTagMetrics.builder()
                            .offererId(offererId)
                            .tagId(tagId)
                            .tagCount(1)
                            .build()));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrementTags(Long offererId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            offererTagMetricsPersistencePort.findByOffererIdAndTagId(offererId, tagId).ifPresent(existing -> {
                existing.decrement();
                offererTagMetricsPersistencePort.update(existing);
            });
        }
    }
}
