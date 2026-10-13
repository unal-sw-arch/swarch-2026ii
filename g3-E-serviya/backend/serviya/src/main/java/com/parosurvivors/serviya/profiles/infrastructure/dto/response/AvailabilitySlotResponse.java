package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

/**
 * Salida web (Response) de una franja de disponibilidad general del oferente.
 * GET /api/v1/offerers/me/availability. weekDay va de 0 (domingo) a 6.
 * TODO: revisar campos.
 */
@Schema(description = "Franja horaria de disponibilidad del oferente")
public record AvailabilitySlotResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long offererId,
        Integer weekDay,
        LocalTime startTime,
        LocalTime endTime,
        Boolean active) {
}
