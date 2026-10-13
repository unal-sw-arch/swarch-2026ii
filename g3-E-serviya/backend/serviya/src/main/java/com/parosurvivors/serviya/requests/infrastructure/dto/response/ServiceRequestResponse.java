package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) de una solicitud de servicio (fila de listado y recurso simple).
 * Mapea desde el dominio ServiceRequest.
 * TODO: revisar campos.
 */
@Schema(description = "Solicitud de servicio")
public record ServiceRequestResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long serviceId,
        Long previousRequestId,
        Long clientId,
        Long offererId,
        Long addressId,
        LocalDateTime scheduledDate,
        String status,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt) {
}
