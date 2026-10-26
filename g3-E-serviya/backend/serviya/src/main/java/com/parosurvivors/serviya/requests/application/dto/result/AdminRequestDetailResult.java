package com.parosurvivors.serviya.requests.application.dto.result;

import com.parosurvivors.serviya.requests.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Result) del detalle de una solicitud para el admin (CQRS-light).
 * Como {@link ServiceRequestDetailResult} pero con campos de auditoria/moderacion adicionales
 * ({@code updatedBy}). Lo compone ServiceRequestQueryService.getRequestDetailForAdmin.
 */
public record AdminRequestDetailResult(
        // Solicitud
        Long id,
        RequestStatus status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt,
        Long updatedBy,
        Long previousRequestId,
        // Servicio
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        // Cliente
        Long clientId,
        String clientName,
        String clientPhotoUrl,
        // Oferente
        Long offererId,
        String offererName,
        String offererPhotoUrl,
        // Ubicacion (direccion descifrada)
        Long addressId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
