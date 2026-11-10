package com.parosurvivors.serviya.services.api.dto;

import java.time.LocalTime;

/**
 * Franja horaria para aplicar la plantilla general del oferente a un servicio.
 */
public record AvailabilityTemplateSlot(
        int weekDay,
        LocalTime startTime,
        LocalTime endTime) {
}
