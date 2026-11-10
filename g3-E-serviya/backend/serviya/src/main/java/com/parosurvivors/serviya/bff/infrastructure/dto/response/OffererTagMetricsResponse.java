package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conteo de un tag de resena para un oferente")
public record OffererTagMetricsResponse(
        Long offererId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
