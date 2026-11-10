package com.parosurvivors.serviya.requests.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle de una solicitud con datos propios de requests (incluidas ambas partes por id).
 *
 * <p>Los nombres, el titulo del servicio, la categoria y la direccion se componen fuera del modulo
 * con {@code profiles.api} y {@code services.api}.</p>
 */
public record RequestDetailSnapshot(
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
        Long clientId,
        Long offererId,
        Long addressId) {
}
