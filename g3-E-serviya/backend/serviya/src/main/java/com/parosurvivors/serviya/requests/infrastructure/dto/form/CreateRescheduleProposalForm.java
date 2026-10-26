package com.parosurvivors.serviya.requests.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Entrada web (Form) para crear una propuesta de reprogramacion (oferente).
 * POST /api/v1/reschedule-proposals (RF-023). El offererId se extrae del JWT.
 * TODO: revisar validaciones.
 */
@Schema(description = "Datos de una propuesta de reprogramacion")
public record CreateRescheduleProposalForm(
        @NotNull Long requestId,
        String reason,
        @NotNull @Future LocalDateTime proposedDate) {
}
