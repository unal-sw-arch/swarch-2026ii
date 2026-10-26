package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Salida web (Response) de la verificacion/geocodificacion de una direccion.
 * POST /api/v1/addresses/verify (RF-009, RNF-019).
 * TODO: revisar campos.
 */
@Schema(description = "Resultado de verificar y geocodificar una direccion")
public record AddressVerificationResponse(
        boolean valid,
        BigDecimal latitude,
        BigDecimal longitude) {
}
