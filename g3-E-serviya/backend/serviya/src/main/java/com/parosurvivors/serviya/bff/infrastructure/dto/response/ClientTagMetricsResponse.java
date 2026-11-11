package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conteo de un tag de resena para un cliente")
public record ClientTagMetricsResponse(
        Long clientId,
        Long tagId,
        String tagName,
        boolean positive,
        Integer tagCount) {
}
