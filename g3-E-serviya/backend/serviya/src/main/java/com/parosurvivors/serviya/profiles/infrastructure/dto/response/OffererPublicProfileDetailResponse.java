package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Salida web (Response) del perfil publico COMPLETO de un oferente.
 * GET /api/v1/offerers/{id}/public-profile (RF-027).
 *
 * <p>Endpoint publico (cliente, administrador o visitante SIN sesion). Deliberadamente no incluye
 * documento ni telefono personal: son PII cifrada y no forman parte de la vitrina publica.</p>
 */
@Schema(description = "Perfil publico de un oferente: identidad, reputacion, metricas y servicios activos")
public record OffererPublicProfileDetailResponse(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String publicDescription,
        @Schema(description = "Canal de contacto publicado por el propio oferente") String whatsappNumber,
        @Schema(description = "Calificacion promedio (0 si aun no tiene calificaciones)") BigDecimal averageRating,
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
