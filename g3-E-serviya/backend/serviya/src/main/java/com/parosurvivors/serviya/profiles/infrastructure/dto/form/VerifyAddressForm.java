package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) para verificar/geocodificar una direccion antes de crearla.
 * POST /api/v1/addresses/verify (RF-009, RNF-019).
 * TODO: revisar validaciones.
 */
@Schema(description = "Direccion a verificar y geocodificar")
public record VerifyAddressForm(
        @NotBlank String addressLine,
        @NotBlank String city) {
}
