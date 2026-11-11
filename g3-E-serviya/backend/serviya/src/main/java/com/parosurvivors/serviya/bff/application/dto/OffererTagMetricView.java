package com.parosurvivors.serviya.bff.application.dto;

/** Conteo de un tag enriquecido con el catalogo de feedback. */
public record OffererTagMetricView(
        Long offererId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
