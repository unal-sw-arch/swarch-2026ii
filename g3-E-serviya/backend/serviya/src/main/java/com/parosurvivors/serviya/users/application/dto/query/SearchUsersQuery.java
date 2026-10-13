package com.parosurvivors.serviya.users.application.dto.query;

/**
 * Entrada de aplicacion (Query) de la busqueda de usuarios del panel admin (RF-068). Campos no-nulos =
 * filtros activos. Vive en el modulo users (dueno del dato); el modulo admin la consume a traves del
 * puerto de entrada. {@code deleted}: null = todos, true = solo eliminados, false = solo activos.
 */
public record SearchUsersQuery(
        String email,
        String fullName,
        String role,
        Boolean banned,
        Boolean deleted) {
}
