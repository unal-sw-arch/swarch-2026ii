package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) del detalle de una solicitud para el admin. Mapea desde
 * {@link com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult}.
 * Como el detalle de parte pero con auditoria ({@code updatedBy}).
 */
@Schema(description = "Detalle de una solicitud de servicio (vista admin)")
public record AdminRequestDetailResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        String status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt,
        Long updatedBy,
        Long previousRequestId,
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        Long clientId,
        String clientName,
        String clientPhotoUrl,
        Long offererId,
        String offererName,
        String offererPhotoUrl,
        Long addressId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
