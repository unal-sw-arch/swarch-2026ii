package com.parosurvivors.serviya.services.api.dto;

import java.time.LocalTime;

/**
 * Franja de disponibilidad de un servicio.
 *
 * <p>{@code weekDay} conserva la convencion de services: 1 = lunes ... 7 = domingo.</p>
 */
public record ServiceAvailabilitySnapshot(
        Long id,
        Long serviceId,
        int weekDay,
        LocalTime startTime,
        LocalTime endTime,
        boolean active) {
}
