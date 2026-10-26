package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) del resumen de una solicitud en un listado paginado (mis solicitudes como
 * cliente / como oferente). Mapea desde {@link com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem}.
 */
@Schema(description = "Resumen de una solicitud de servicio en un listado")
public record ServiceRequestSummaryResponse(
        Long requestId,
        String status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        Long previousRequestId,
        LocalDateTime createdAt,
        Long serviceId,
        String serviceTitle,
        String categoryName,
        Long counterpartyId,
        String counterpartyName,
        String counterpartyPhotoUrl,
        String city) {
}
