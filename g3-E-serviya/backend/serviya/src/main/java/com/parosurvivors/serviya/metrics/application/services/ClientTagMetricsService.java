package com.parosurvivors.serviya.metrics.application.services;

import com.parosurvivors.serviya.metrics.application.ports.input.ClientTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.output.ClientTagMetricsPersistencePort;
import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conteo de tags recibidas por el cliente. UPSERT por tag disparado por los eventos de feedback del
 * oferente al cliente.
 */
@Component
@RequiredArgsConstructor
public class ClientTagMetricsService implements ClientTagMetricsServicePort {

    private final ClientTagMetricsPersistencePort clientTagMetricsPersistencePort;

    @Override
    public List<ClientTagMetrics> getTagMetrics(Long clientId) {
        return clientTagMetricsPersistencePort.findByClientId(clientId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementTags(Long clientId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            clientTagMetricsPersistencePort.findByClientIdAndTagId(clientId, tagId).ifPresentOrElse(
                    existing -> {
                        existing.increment();
                        clientTagMetricsPersistencePort.update(existing);
                    },
                    () -> clientTagMetricsPersistencePort.save(ClientTagMetrics.builder()
                            .clientId(clientId)
                            .tagId(tagId)
                            .tagCount(1)
                            .build()));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrementTags(Long clientId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            clientTagMetricsPersistencePort.findByClientIdAndTagId(clientId, tagId).ifPresent(existing -> {
                existing.decrement();
                clientTagMetricsPersistencePort.update(existing);
            });
        }
    }
}
