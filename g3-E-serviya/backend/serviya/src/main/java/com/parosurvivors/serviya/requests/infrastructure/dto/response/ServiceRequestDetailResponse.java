package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) del detalle de una solicitud para una parte. Mapea desde
 * {@link com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult}.
 * Incluye la direccion descifrada (addressLine) y coordenadas para coordinar el lugar de prestacion.
 * Muestra solo la CONTRAPARTE (quien consulta ya es una de las partes).
 */
@Schema(description = "Detalle de una solicitud de servicio")
public record ServiceRequestDetailResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        String status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt,
        Long previousRequestId,
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        Long counterpartyId,
        String counterpartyName,
        String counterpartyPhotoUrl,
        Long addressId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
