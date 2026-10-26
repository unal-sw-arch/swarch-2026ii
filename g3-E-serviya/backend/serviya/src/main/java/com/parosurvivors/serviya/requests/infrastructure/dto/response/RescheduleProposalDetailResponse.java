package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida web (Response) del detalle de una propuesta de reprogramacion. Mapea desde
 * RescheduleProposalDetailResult. La otra parte es relativa a quien consulta.
 */
@Schema(description = "Detalle de una propuesta de reprogramacion (propuesta + solicitud + servicio + otra parte)")
public record RescheduleProposalDetailResponse(
        Long proposalId,
        String status,
        String reason,
        LocalDateTime proposedDate,
        LocalDateTime createdAt,
        LocalDateTime respondedAt,
        Long requestId,
        String requestStatus,
        LocalDateTime originalScheduledDate,
        BigDecimal requestedPrice,
        String addressLabel,
        Long previousRequestId,
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        Long counterpartyUserId,
        String counterpartyName,
        String counterpartyPhotoUrl) {
}
