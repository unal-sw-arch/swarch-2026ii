package com.parosurvivors.serviya.bff.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle compuesto de un servicio: datos del servicio + oferente + metricas + feedback + horarios.
 */
public record ServiceDetailView(
        Long id,
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String whatsappNumber,
        String publicDescription,
        BigDecimal offererAverageRating,
        CategoryView category,
        List<FeedbackView> feedbacks,
        List<AvailabilityView> availability,
        String title,
        String description,
        List<String> photos,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        Boolean active,
        BigDecimal operationRadiusKm,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        BigDecimal serviceAverageRating,
        Integer serviceTotalRatings,
        Integer serviceTotalComments,
        Integer totalCompletedServices,
        Integer totalCancelledServices) {

    public record CategoryView(Long id, String name) {
    }

    public record FeedbackView(
            Long id,
            Long requestId,
            Long userId,
            String userName,
            String userPhotoUrl,
            String comment,
            Integer rating,
            LocalDateTime createdAt) {
    }

    public record AvailabilityView(
            Long id,
            byte weekDay,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            boolean activeStatus) {
    }
}
