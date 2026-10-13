package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) del resumen de una propuesta en un listado paginado (recibidas/enviadas).
 * Mapea desde RescheduleProposalItem.
 */
@Schema(description = "Resumen de una propuesta de reprogramacion en un listado")
public record RescheduleProposalSummaryResponse(
        Long proposalId,
        String status,
        LocalDateTime originalScheduledDate,
        LocalDateTime proposedDate,
        String serviceTitle,
        String counterpartyName,
        String counterpartyPhotoUrl,
        LocalDateTime createdAt) {
}
