package com.parosurvivors.serviya.profiles.api.dto;

/**
 * Resumen del oferente para listados y cabeceras de perfil publico.
 *
 * <p>No incluye {@code averageRating}: es un dato de metrics y el consumidor/BFF lo compone con
 * {@code metrics.api}. Asi profiles no depende de metrics en su contrato publico.</p>
 */
public record OffererProfileSummarySnapshot(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty) {
}
