package com.parosurvivors.serviya.requests.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Salida web (Response) de una propuesta de reprogramacion. Mapea desde el dominio RescheduleProposal.
 * TODO: revisar campos.
 */
@Schema(description = "Propuesta de reprogramacion")
public record RescheduleProposalResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long requestId,
        String reason,
        LocalDateTime proposedDate,
        String status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt) {
}
