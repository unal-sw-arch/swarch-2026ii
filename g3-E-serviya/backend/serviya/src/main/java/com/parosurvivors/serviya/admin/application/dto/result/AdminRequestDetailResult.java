package com.parosurvivors.serviya.admin.application.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle administrativo de una solicitud (ambas partes). Compuesto por el BFF de admin con
 * {@code requests.api}, {@code services.api} y {@code profiles.api}; los estados viajan como String.
 */
public record AdminRequestDetailResult(
        Long id,
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
