package com.parosurvivors.serviya.profiles.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada web (Form) para fijar la direccion principal del usuario. PATCH /api/v1/users/me/main-address.
 * TODO: revisar validaciones.
 */
@Schema(description = "Direccion a marcar como principal")
public record UpdateMainAddressForm(
        Long addressId) {
}
