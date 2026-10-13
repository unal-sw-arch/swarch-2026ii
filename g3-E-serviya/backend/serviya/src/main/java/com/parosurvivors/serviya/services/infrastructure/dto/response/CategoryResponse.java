package com.parosurvivors.serviya.services.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representacion de una categoría del marketplace")
public record CategoryResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
    String name
) {}
