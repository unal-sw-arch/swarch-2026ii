package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Entrada web (Form) de actualizacion parcial de una direccion. PATCH /api/v1/addresses/{id} (RF-009).
 * Solo campos no-nulos. El id va en el path; el dueno se verifica en el servicio.
 * TODO: revisar validaciones.
 */
@Schema(description = "Campos editables de una direccion (PATCH parcial)")
public record UpdateAddressForm(
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
