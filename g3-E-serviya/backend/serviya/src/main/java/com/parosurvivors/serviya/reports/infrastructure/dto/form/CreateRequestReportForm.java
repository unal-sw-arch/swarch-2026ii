package com.parosurvivors.serviya.reports.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada web (Form) para reportar el incumplimiento de una solicitud.
 * POST /api/v1/reports/requests (RF-073).
 *
 * <p>El {@code reporterId} sale del JWT y el usuario REPORTADO se deriva en el servicio a partir de la
 * contraparte de {@code requestId} (si reporta el cliente, se reporta al oferente, y viceversa).
 *
 * <p>Deliberadamente NO existe aqui un campo {@code reportedUserId}: aceptarlo del cliente permitiria
 * enviar el id de un tercero y culparlo de un incumplimiento en el que no participo. Al derivarlo de la
 * solicitud, eso es imposible por construccion.</p>
 */
@Schema(description = "Datos para reportar el incumplimiento de una solicitud")
public record CreateRequestReportForm(
        @NotBlank String category,
        String customCategory,
        @NotBlank String reason,
        @NotNull Long requestId) {
}
