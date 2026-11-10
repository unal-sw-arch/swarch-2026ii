package com.parosurvivors.serviya.profiles.api.dto;

import java.time.LocalTime;

/**
 * Franja de disponibilidad del oferente.
 *
 * <p>{@code weekDay} conserva la convencion de profiles: 0 = lunes ... 6 = domingo.</p>
 */
public record OffererAvailabilitySnapshot(
        Long id,
        Long offererId,
        int weekDay,
        LocalTime startTime,
        LocalTime endTime,
        boolean active) {
}
