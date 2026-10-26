package com.parosurvivors.serviya.reports.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Salida web: resumen de una parte del reporte (identidad + nombre + foto). Mapea desde PartySummary. */
@Schema(description = "Resumen de una parte del reporte (reportante o reportado)")
public record PartySummaryResponse(
        Long userId,
        String fullName,
        String photoUrl) {
}
