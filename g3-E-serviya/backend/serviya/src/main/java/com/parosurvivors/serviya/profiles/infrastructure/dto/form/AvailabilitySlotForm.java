package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Entrada web (Form) de una franja de la disponibilidad general del oferente. Elemento de la lista
 * enviada en PUT /api/v1/offerers/me/availability (RF-072), que reemplaza el horario completo.
 * weekDay va de 0 (domingo) a 6.
 * TODO: revisar validaciones.
 */
@Schema(description = "Franja horaria de disponibilidad (elemento del reemplazo masivo)")
public record AvailabilitySlotForm(
        @NotNull Integer weekDay,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Boolean active) {
}
