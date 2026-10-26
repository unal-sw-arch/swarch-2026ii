package com.parosurvivors.serviya.metrics.application.ports.input;

import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;

import java.util.List;

/**
 * Puerto de entrada de ClientTagMetricsService. Consulta + escritura por eventos (UPSERT por tag):
 * los adaptadores del feedback al cliente traducen el evento a increment/decrementTags y delegan.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 6).
 */
public interface ClientTagMetricsServicePort {

    List<ClientTagMetrics> getTagMetrics(Long clientId);

    /** Por cada tag de la reseña del cliente aplica UPSERT del conteo (clientId, tagId). */
    void incrementTags(Long clientId, List<Long> tagIds);

    /** Por cada tag de la reseña borrada decrementa el conteo (clientId, tagId), sin bajar de 0. */
    void decrementTags(Long clientId, List<Long> tagIds);
}
