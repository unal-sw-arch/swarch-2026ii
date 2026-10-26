package com.parosurvivors.serviya.requests.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Entrada web (Form) para reprogramar una solicitud (cliente). POST /api/v1/service-requests/{id}/reschedule (RF-033).
 * Crea una NUEVA solicitud enlazada via previousRequestId. El id de la solicitud original va en el path.
 * TODO: revisar validaciones.
 */
@Schema(description = "Nueva fecha propuesta para reprogramar la solicitud")
public record RescheduleRequestForm(
        @NotNull @Future LocalDateTime newDate) {
}
