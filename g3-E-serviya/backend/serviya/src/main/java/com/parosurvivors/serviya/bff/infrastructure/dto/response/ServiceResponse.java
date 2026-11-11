package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Representacion de un servicio en busquedas/listados enriquecidos por el BFF.
 */
@Schema(description = "Representacion de un servicio del marketplace")
public record ServiceResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long offererId,
        String offererName,
        String title,
        String description,
        List<String> photos,
        BigDecimal priceHourly,
        Long categoryId,
        Integer averageDurationMinutes,
        Boolean active,
        BigDecimal operationRadiusKm,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments) {
}
