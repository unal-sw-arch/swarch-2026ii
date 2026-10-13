package com.parosurvivors.serviya.metrics.application.ports.input;

import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;

import java.util.List;

/**
 * Puerto de entrada de OffererTagMetricsService. Consulta + escritura por eventos (UPSERT por tag):
 * los adaptadores del feedback de servicio traducen el evento a increment/decrementTags y delegan.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 6).
 */
public interface OffererTagMetricsServicePort {

    List<OffererTagMetrics> getTagMetrics(Long offererId);

    /** Por cada tag de la reseña de servicio aplica UPSERT del conteo (offererId, tagId). */
    void incrementTags(Long offererId, List<Long> tagIds);

    /** Por cada tag de la reseña borrada decrementa el conteo (offererId, tagId), sin bajar de 0. */
    void decrementTags(Long offererId, List<Long> tagIds);
}
