package com.parosurvivors.serviya.reports.application.dto.result;

/**
 * Resumen de una parte del reporte (reportante o reportado) para el detalle de moderación:
 * identidad + nombre + foto. No expone PII (documento/teléfono). {@code fullName}/{@code photoUrl}
 * pueden venir null si el usuario no tiene perfil.
 */
public record PartySummary(
        Long userId,
        String fullName,
        String photoUrl) {
}
