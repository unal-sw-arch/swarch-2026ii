package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;

/**
 * Resumen publico de un oferente: perfiles + rating de metrics.
 */
public record OffererSummaryView(
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        BigDecimal averageRating) {
}
