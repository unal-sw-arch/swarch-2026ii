package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista de la vitrina publica de un oferente, compuesta por el BFF con profiles, metrics y services.
 */
public record OffererPublicProfileView(
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
        List<PublishedServiceView> services) {

    public record PublishedServiceView(
            Long id,
            String title,
            String description,
            BigDecimal priceHourly,
            String categoryName,
            Integer averageDurationMinutes) {
    }
}
