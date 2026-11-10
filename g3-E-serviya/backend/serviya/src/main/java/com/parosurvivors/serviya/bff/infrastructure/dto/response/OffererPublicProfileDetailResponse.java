package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Perfil publico completo de un oferente. Misma forma JSON que el endpoint historico de profiles.
 */
@Schema(description = "Perfil publico de un oferente: identidad, reputacion, metricas y servicios activos")
public record OffererPublicProfileDetailResponse(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String publicDescription,
        String whatsappNumber,
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments,
        Integer totalPositiveTags,
        Integer totalNegativeTags,
        Integer totalCompletedServices,
        Integer totalCancelledServices,
        Integer totalNotProvidedServices,
        List<PublishedServiceResponse> services) {

    @Schema(description = "Servicio activo publicado por el oferente")
    public record PublishedServiceResponse(
            Long id,
            String title,
            String description,
            BigDecimal priceHourly,
            String categoryName,
            Integer averageDurationMinutes) {
    }
}
