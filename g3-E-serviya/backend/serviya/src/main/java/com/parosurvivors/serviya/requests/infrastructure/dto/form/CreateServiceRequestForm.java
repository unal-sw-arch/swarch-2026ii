package com.parosurvivors.serviya.requests.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Entrada web (Form) para crear una solicitud de servicio. POST /api/v1/service-requests (RF-029). CLIENT.
 * El clientId se extrae del JWT en el controller.
 * TODO: revisar validaciones.
 */
@Schema(description = "Datos para crear una solicitud de servicio")
public record CreateServiceRequestForm(
        @NotNull Long serviceId,
        @NotNull Long addressId,
        @NotNull @Future LocalDateTime scheduledDate) {
}
