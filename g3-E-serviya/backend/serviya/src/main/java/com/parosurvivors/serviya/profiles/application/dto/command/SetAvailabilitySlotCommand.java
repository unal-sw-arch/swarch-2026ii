package com.parosurvivors.serviya.profiles.application.dto.command;

import java.time.LocalTime;

/**
 * Entrada de aplicacion (Command) de una franja al fijar la disponibilidad general del oferente.
 * Elemento de la lista de setSchedule (reemplazo masivo). weekDay va de 0 (domingo) a 6.
 */
public record SetAvailabilitySlotCommand(
        Integer weekDay,
        LocalTime startTime,
        LocalTime endTime,
        Boolean active) {
}
