package com.parosurvivors.serviya.bff.application.dto;

/** Conteo de un tag de cliente enriquecido con el catalogo de feedback. */
public record ClientTagMetricView(
        Long clientId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
