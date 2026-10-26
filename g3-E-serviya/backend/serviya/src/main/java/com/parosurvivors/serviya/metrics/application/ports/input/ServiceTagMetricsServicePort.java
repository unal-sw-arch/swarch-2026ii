package com.parosurvivors.serviya.metrics.application.ports.input;

import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;

import java.util.List;

/**
 * Puerto de entrada de ServiceTagMetricsService. Consulta + escritura por eventos (UPSERT por tag):
 * los adaptadores {@code @TransactionalEventListener} de feedback traducen el evento a increment/
 * decrementTags y delegan. Ver documents/project-structure/estructura-servicios.docx (módulo 6).
 */
public interface ServiceTagMetricsServicePort {

    List<ServiceTagMetrics> getTagMetrics(Long serviceId);

    /** Por cada tag de la reseña creada aplica UPSERT del conteo (serviceId, tagId). */
    void incrementTags(Long serviceId, List<Long> tagIds);

    /** Por cada tag de la reseña borrada decrementa el conteo (serviceId, tagId), sin bajar de 0. */
    void decrementTags(Long serviceId, List<Long> tagIds);
}
