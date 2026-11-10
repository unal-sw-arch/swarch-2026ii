package com.parosurvivors.serviya.bff.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Detalle compuesto de un servicio. Misma forma JSON que el endpoint historico de services.
 */
@Schema(description = "Detalle de un servicio con informacion del oferente")
public record ServiceDetailResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long userId,
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String whatsappNumber,
        String publicDescription,
        BigDecimal offererAverageRating,
        CategoryResponse category,
        List<FeedbackResponse> feedbacks,
        List<ServiceAvailabilityResponse> availability,
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

    public record CategoryResponse(Long id, String name) {
    }

    public record FeedbackResponse(
            Long id,
            Long requestId,
            Long userId,
            String userName,
            String userPhotoUrl,
            String comment,
            Integer rating,
            LocalDateTime createdAt) {
    }

    public record ServiceAvailabilityResponse(
            Long id,
            byte weekDay,
            LocalTime startTime,
            LocalTime endTime,
            boolean activeStatus) {
    }
}
