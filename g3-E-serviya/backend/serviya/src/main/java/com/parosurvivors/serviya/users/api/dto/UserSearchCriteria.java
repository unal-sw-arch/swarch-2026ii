package com.parosurvivors.serviya.users.api.dto;

/**
 * Filtros de busqueda de cuentas. Campos {@code null} = filtro no aplicado.
 */
public record UserSearchCriteria(
        String email,
        String fullName,
        String role,
        Boolean banned,
        Boolean deleted) {
}
