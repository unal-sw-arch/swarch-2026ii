package com.parosurvivors.serviya.requests.application.dto.command;

import java.time.LocalDateTime;

/**
 * Entrada de aplicacion (Command) para crear una solicitud de servicio. El clientId proviene del JWT.
 */
public record CreateServiceRequestCommand(
        Long clientId,
        Long serviceId,
        Long addressId,
        LocalDateTime scheduledDate) {
}
