package com.parosurvivors.serviya.profiles.api.dto;

import java.math.BigDecimal;

/**
 * Comando publico para crear una direccion.
 */
public record CreateAddressCommand(
        Long userId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
