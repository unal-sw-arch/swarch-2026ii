package com.parosurvivors.serviya.profiles.application.dto.result;

import java.math.BigDecimal;

/**
 * Salida de aplicacion (Result) de la geocodificacion de una direccion.
 * Devuelto por AddressService.getCoordinates; usado por la verificacion de direccion.
 * Sustituye al antiguo placeholder CoordinatesDTO.
 */
public record CoordinatesResult(
        BigDecimal latitude,
        BigDecimal longitude) {
}
