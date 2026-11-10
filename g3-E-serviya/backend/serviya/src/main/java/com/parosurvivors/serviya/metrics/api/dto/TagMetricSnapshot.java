package com.parosurvivors.serviya.metrics.api.dto;

/**
 * Conteo de un tag para un servicio, oferente o cliente.
 *
 * <p>Solo lleva id y conteo. El nombre/sentimiento se enriquece desde {@code feedback.api} en el
 * consumidor/BFF.</p>
 */
public record TagMetricSnapshot(Long tagId, int count) {
}
