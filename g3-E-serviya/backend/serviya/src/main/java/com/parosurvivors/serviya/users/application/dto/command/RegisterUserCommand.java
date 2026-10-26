package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Entrada de aplicacion (Command) del caso de uso registrar usuario.
 * Espejo de RegisterUserForm sin anotaciones de web/validacion.
 * TODO: revisar campos.
 */
public record RegisterUserCommand(
        String email,
        String password,
        String fullName,
        String role,
        String documentType,
        String documentNumber,
        String phone,
        Boolean acceptedTerms,
        // Direccion principal opcional capturada en el registro (los 4 campos van juntos).
        String addressLine,
        String city,
        java.math.BigDecimal latitude,
        java.math.BigDecimal longitude) {
}
