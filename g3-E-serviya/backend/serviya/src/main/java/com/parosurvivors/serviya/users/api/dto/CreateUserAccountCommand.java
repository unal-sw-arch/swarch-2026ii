package com.parosurvivors.serviya.users.api.dto;

import java.math.BigDecimal;

/**
 * Comando publico de creacion de cuenta.
 *
 * <p>Incluye los datos de perfil y direccion que el flujo de identity necesita para orquestar la
 * creacion inicial. El rol viaja como {@code String} (ver {@code shared.contract.Roles}).</p>
 */
public record CreateUserAccountCommand(
        String email,
        String password,
        String fullName,
        String role,
        String documentType,
        String documentNumber,
        String phone,
        Boolean acceptedTerms,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
