package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Entrada web (Form) para crear una direccion. POST /api/v1/users/me/addresses (RF-009).
 * El userId se extrae del JWT. addressLine se cifra (AES-256-GCM) antes de persistir.
 * TODO: revisar validaciones.
 */
@Schema(description = "Datos para crear una direccion")
public record CreateAddressForm(
        @NotBlank String addressLine,
        @NotBlank String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
