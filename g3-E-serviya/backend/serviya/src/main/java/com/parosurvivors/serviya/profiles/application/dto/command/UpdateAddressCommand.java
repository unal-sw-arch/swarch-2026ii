package com.parosurvivors.serviya.profiles.application.dto.command;

import java.math.BigDecimal;

/**
 * Entrada de aplicacion (Command) para actualizar parcialmente una direccion. El addressId va en el path;
 * la verificacion de propiedad la hace el servicio con el requesterId del JWT. Campos no-nulos = a actualizar.
 */
public record UpdateAddressCommand(
        Long addressId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
