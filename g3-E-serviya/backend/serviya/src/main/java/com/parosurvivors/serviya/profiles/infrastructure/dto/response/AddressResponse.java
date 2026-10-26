package com.parosurvivors.serviya.profiles.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Salida web (Response) de una direccion. GET/POST/PATCH bajo /api/v1/users/me/addresses y /addresses/{id}.
 * addressLine ya viene descifrado desde el dominio.
 * TODO: revisar campos.
 */
@Schema(description = "Direccion de un usuario")
public record AddressResponse(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        Long userId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
