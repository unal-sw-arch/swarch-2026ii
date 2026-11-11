package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fila de servicio en busquedas/listados enriquecidos por el BFF.
 */
public record ServiceSummaryView(
        Long id,
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

    public ServiceSummaryView {
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}
