package com.parosurvivors.serviya.profiles.application.dto.command;

import java.math.BigDecimal;

/**
 * Entrada de aplicacion (Command) para crear una direccion. El userId proviene del JWT.
 */
public record CreateAddressCommand(
        Long userId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
