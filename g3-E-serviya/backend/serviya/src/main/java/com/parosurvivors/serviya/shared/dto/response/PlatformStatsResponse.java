package com.parosurvivors.serviya.shared.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estadísticas generales de la plataforma")
public record PlatformStatsResponse(
    @Schema(description = "Total de oferentes activos")
    Long activeOfferers,
    
    @Schema(description = "Total de servicios completados")
    Long completedServices,
    
    @Schema(description = "Calificación promedio de la plataforma (0-5)")
    Double averageRating,
    
    @Schema(description = "Total de categorías disponibles")
    Long totalCategories
) {}
